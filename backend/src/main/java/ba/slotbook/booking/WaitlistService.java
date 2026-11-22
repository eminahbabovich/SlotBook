package ba.slotbook.booking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.availability.SlotService;
import ba.slotbook.availability.TimeRange;
import ba.slotbook.booking.WaitlistNotNeededException.Razlog;
import ba.slotbook.error.InvalidFieldException;
import ba.slotbook.notification.NotificationService;
import ba.slotbook.notification.NotificationType;
import ba.slotbook.resource.Resource;
import ba.slotbook.resource.ResourceNotFoundException;
import ba.slotbook.resource.ResourceRepository;
import ba.slotbook.user.UserRepository;

@Service
public class WaitlistService {

	// Koliko dugo prvi na listi ima termin samo za sebe. Kratko, jer termin za to vrijeme niko drugi ne može uzeti.
	static final Duration ROK_PONUDE = Duration.ofMinutes(30);

	private final WaitlistEntryRepository entries;
	private final BookingRepository bookings;
	private final ResourceRepository resources;
	private final UserRepository users;
	private final SlotService slotService;
	private final NotificationService notifications;
	private final Clock clock;
	private final String appUrl;

	public WaitlistService(WaitlistEntryRepository entries, BookingRepository bookings, ResourceRepository resources,
			UserRepository users, SlotService slotService, NotificationService notifications, Clock clock,
			@Value("${slotbook.app-url}") String appUrl) {
		this.entries = entries;
		this.bookings = bookings;
		this.resources = resources;
		this.users = users;
		this.slotService = slotService;
		this.notifications = notifications;
		this.clock = clock;
		this.appUrl = appUrl;
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
		List<Booking> drze = bookings.findByResourceIdAndStatusInAndStartTimeBeforeAndEndTimeAfter(resourceId,
				BookingStatus.DRZE_TERMIN, termin.end(), termin.start());
		if (drze.stream().anyMatch(b -> b.getClient().getId().equals(clientId))) {
			throw new WaitlistNotNeededException(Razlog.TVOJ);
		}
		// Termin ponuđen nekom drugom je za ostale zauzet, pa se na njega može čekati
		Instant sada = clock.instant();
		if (drze.isEmpty() && !entries.isOfferedToOther(resourceId, termin.start(), termin.end(), clientId, sada)) {
			throw new WaitlistNotNeededException(Razlog.SLOBODAN);
		}
		return WaitlistEntryResponse.from(entries.save(new WaitlistEntry(resurs, users.getReferenceById(clientId),
				termin, sada)));
	}

	// Vlasništvo je provjerio @PreAuthorize (WaitlistAccess)
	@Transactional
	public void leave(UUID entryId) {
		entries.findById(entryId).orElseThrow().otkazi();
	}

	// Za BookingJob, poslije isteka rezervacija, da istekla PENDING već oslobodi termin. Termin se nudi
	// najkasnije minutu poslije otkazivanja, a ne u istoj transakciji: tako ga oslobodi i otkazivanje
	// SQL naredbom (istek, brisanje naloga), a ne samo BookingService.
	@Transactional
	public int offerFreed() {
		Instant sada = clock.instant();
		entries.expireAll(sada);
		int ponude = 0;
		for (UUID id : entries.findFirstInLineForFreeSlots(sada)) {
			WaitlistEntry upis = entries.findWithResourceById(id).orElseThrow();
			UUID resourceId = upis.getResource().getId();
			// Rezervacija koja je stigla poslije upita iznad je ovdje već commitana ili čeka ovaj lock,
			// a poslije njega vidi ponudu i dobije 409
			resources.findLockedById(resourceId);
			boolean zauzet = !bookings.findByResourceIdAndStatusInAndStartTimeBeforeAndEndTimeAfter(resourceId,
					BookingStatus.DRZE_TERMIN, upis.getEndTime(), upis.getStartTime()).isEmpty();
			if (zauzet) {
				continue;
			}
			Instant rok = sada.plus(ROK_PONUDE).isBefore(upis.getStartTime()) ? sada.plus(ROK_PONUDE)
					: upis.getStartTime();
			if (entries.offer(id, rok) == 1) {
				notifications.enqueue(upis.getClient().getId(), NotificationType.WAITLIST_OFFER,
						BookingEmails.ponuda(upis, rok, appUrl));
				ponude++;
			}
		}
		return ponude;
	}

}
