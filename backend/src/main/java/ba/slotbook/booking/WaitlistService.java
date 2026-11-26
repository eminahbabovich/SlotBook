package ba.slotbook.booking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import ba.slotbook.availability.SlotService;
import ba.slotbook.availability.TimeRange;
import ba.slotbook.booking.WaitlistNotNeededException.Razlog;
import ba.slotbook.error.InvalidFieldException;
import ba.slotbook.notification.NotificationService;
import ba.slotbook.notification.NotificationType;
import ba.slotbook.paging.PageResponse;
import ba.slotbook.resource.Resource;
import ba.slotbook.resource.ResourceNotFoundException;
import ba.slotbook.resource.ResourceRepository;
import ba.slotbook.user.UserRepository;

@Service
public class WaitlistService {

	// Koliko dugo prvi na listi ima termin samo za sebe. Kratko, jer termin za to vrijeme niko drugi ne može uzeti.
	static final Duration ROK_PONUDE = Duration.ofMinutes(30);

	private final WaitlistEntryRepository entries;
	private final SlobodnaMjesta slobodnaMjesta;
	private final BookingRules pravila;
	private final ResourceRepository resources;
	private final UserRepository users;
	private final SlotService slotService;
	private final NotificationService notifications;
	private final ApplicationEventPublisher objave;
	private final Clock clock;
	private final String appUrl;
	private final TransactionTemplate transakcija;

	public WaitlistService(WaitlistEntryRepository entries, SlobodnaMjesta slobodnaMjesta, BookingRules pravila,
			ResourceRepository resources,
			UserRepository users, SlotService slotService, NotificationService notifications,
			ApplicationEventPublisher objave, Clock clock, PlatformTransactionManager transactionManager,
			@Value("${slotbook.app-url}") String appUrl) {
		this.entries = entries;
		this.slobodnaMjesta = slobodnaMjesta;
		this.pravila = pravila;
		this.resources = resources;
		this.users = users;
		this.slotService = slotService;
		this.notifications = notifications;
		this.objave = objave;
		this.clock = clock;
		this.appUrl = appUrl;
		this.transakcija = new TransactionTemplate(transactionManager);
	}

	// Lock resursa kao kod rezervacije: dupli klik čeka prvi zahtjev i ovdje nađe njegov upis.
	// Isti upis se vraća i drugi put, bez greške, jer klijent ionako hoće isto.
	@Transactional
	public WaitlistEntryResponse join(UUID resourceId, UUID clientId, Instant start) {
		Resource resurs = resources.findLockedById(resourceId).orElseThrow(ResourceNotFoundException::new);
		var postojeci = entries.findByResourceIdAndClientIdAndStartTimeAndStatusIn(resourceId, clientId, start,
				WaitlistStatus.ZIVI);
		if (postojeci.isPresent()) {
			return WaitlistEntryResponse.from(postojeci.get());
		}
		TimeRange termin = slotService.offered(resourceId, start)
				.orElseThrow(() -> new InvalidFieldException("startTime",
						"Ovaj termin se ne nudi. Izaberi jedan od termina u kalendaru."));
		Instant sada = clock.instant();
		SlobodnaMjesta.Stanje stanje = slobodnaMjesta.za(resurs, clientId, termin, sada);
		if (stanje.mojeVec()) {
			throw new WaitlistNotNeededException(Razlog.TVOJ);
		}
		// Na listu se čeka kad za klijenta nema mjesta: sva su zauzeta ili ponuđena drugima
		if (stanje.mjesto().isPresent()) {
			throw new WaitlistNotNeededException(Razlog.SLOBODAN);
		}
		// Ista pravila kao za rezervaciju: ponuda blokiranom članu bi 30 minuta držala termin uzalud.
		// Granica budućih rezervacija se ponovo provjerava kad prihvati ponudu, jer se do tada promijeni.
		pravila.provjeri(resurs, clientId, List.of(termin), sada);
		return WaitlistEntryResponse.from(entries.save(new WaitlistEntry(resurs, users.getReferenceById(clientId),
				termin, sada)));
	}

	// Samo živi upisi, najbliži termin prvo. Završeni (rezervisan, istekao) nisu lista čekanja, a
	// rezervisan termin je ionako u mojim rezervacijama.
	@Transactional(readOnly = true)
	public PageResponse<MyWaitlistEntryResponse> mine(UUID clientId, Pageable pageable) {
		Pageable poPocetku = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
				Sort.by(Sort.Order.asc("startTime"), Sort.Order.asc("id")));
		return PageResponse.from(entries.findMine(clientId, WaitlistStatus.ZIVI, poPocetku)
				.map(MyWaitlistEntryResponse::from));
	}

	// Vlasništvo je provjerio @PreAuthorize (WaitlistAccess)
	@Transactional
	public void leave(UUID entryId) {
		entries.findById(entryId).orElseThrow().otkazi();
	}

	// Za BookingJob, poslije isteka rezervacija, da istekla PENDING već oslobodi termin. Termin se nudi
	// najkasnije minutu poslije otkazivanja, a ne u istoj transakciji: tako ga oslobodi i otkazivanje
	// SQL naredbom (istek, brisanje naloga), a ne samo BookingService.
	// Istek ide u svoju transakciju, commitanu prije prvog locka resursa. Rezervacija zaključa resurs pa
	// mijenja upis na listi (markBooked), pa bi posao koji drži istekle upise i čeka resurs s njom ušao u
	// deadlock (40P01). Ovako oba puta zaključavaju istim redom: prvo resurs, pa upis.
	public int offerFreed() {
		Instant sada = clock.instant();
		transakcija.executeWithoutResult(t -> entries.expireAll(sada)
				.forEach(resourceId -> objave.publishEvent(new SlotsChanged(resourceId))));
		return transakcija.execute(t -> ponudi(sada));
	}

	private int ponudi(Instant sada) {
		int ponude = 0;
		for (UUID id : entries.findFirstInLineForFreeSlots(sada)) {
			WaitlistEntry upis = entries.findWithResourceById(id).orElseThrow();
			UUID resourceId = upis.getResource().getId();
			// Rezervacija koja je stigla poslije upita iznad je ovdje već commitana ili čeka ovaj lock,
			// a poslije njega vidi ponudu i dobije 409
			Resource resurs = resources.findLockedById(resourceId).orElseThrow();
			TimeRange termin = new TimeRange(upis.getStartTime(), upis.getEndTime());
			// Svaka ponuda koja traje čuva jedno mjesto, pa se nudi samo mjesto koje niko ne drži
			if (slobodnaMjesta.za(resurs, upis.getClient().getId(), termin, sada).mjesto().isEmpty()) {
				continue;
			}
			Instant rok = sada.plus(ROK_PONUDE).isBefore(upis.getStartTime()) ? sada.plus(ROK_PONUDE)
					: upis.getStartTime();
			if (entries.offer(id, rok) == 1) {
				notifications.enqueue(upis.getClient().getId(), NotificationType.WAITLIST_OFFER,
						BookingEmails.ponuda(upis, rok, appUrl));
				// Drugima se termin sad prikazuje kao zauzet, a onome kome je ponuđen kao njegov
				objave.publishEvent(new SlotsChanged(resourceId));
				ponude++;
			}
		}
		return ponude;
	}

}
