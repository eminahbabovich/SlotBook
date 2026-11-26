package ba.slotbook.booking;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.availability.SlotService;
import ba.slotbook.availability.SlotsResponse;
import ba.slotbook.availability.TimeRange;
import ba.slotbook.booking.SlotCalendarResponse.FreeSlot;
import ba.slotbook.booking.SlotCalendarResponse.OfferedSlot;
import ba.slotbook.booking.SlotCalendarResponse.TakenSlot;
import ba.slotbook.organization.OrganizationRole;
import ba.slotbook.organization.OrganizationService;
import ba.slotbook.resource.Resource;
import ba.slotbook.resource.ResourceRepository;

@Service
public class FreeSlotService {

	private final SlotService slotService;
	private final BookingRepository bookings;
	private final WaitlistEntryRepository waitlist;
	private final ResourceRepository resources;
	private final OrganizationService organizations;
	private final Clock clock;

	public FreeSlotService(SlotService slotService, BookingRepository bookings, WaitlistEntryRepository waitlist,
			ResourceRepository resources, OrganizationService organizations, Clock clock) {
		this.slotService = slotService;
		this.bookings = bookings;
		this.waitlist = waitlist;
		this.resources = resources;
		this.organizations = organizations;
		this.clock = clock;
	}

	// "Nađi slobodno": isti račun kao kalendar, za svaki aktivan resurs, pa filter po satu u zoni
	// organizacije (od fromHour uključivo do toHour isključivo, po početku termina). Klijentu se ne nude
	// termini koje po pravilima resursa ne bi mogao uzeti, kao ni u kalendaru.
	@Transactional(readOnly = true)
	public OrganizationFreeSlotsResponse inOrganization(UUID organizationId, UUID userId, LocalDate from, int days,
			int fromHour, int toHour) {
		boolean klijent = organizations.findRole(organizationId, userId)
				.map(uloga -> uloga == OrganizationRole.CLIENT)
				.orElse(true);
		Instant sada = clock.instant();
		List<OrganizationFreeSlotsResponse.Slot> termini = new ArrayList<>();
		String zona = null;
		for (Resource resurs : resources.findByOrganizationIdOrderByNameAscIdAsc(organizationId)) {
			if (!resurs.isActive()) {
				continue;
			}
			SlotCalendarResponse kalendar = calendar(resurs.getId(), from, from.plusDays(days), userId);
			zona = kalendar.timezone();
			ZoneId zonaId = ZoneId.of(zona);
			for (FreeSlot t : kalendar.slots()) {
				int sat = t.start().atZone(zonaId).getHour();
				if (sat < fromHour || sat >= toHour) {
					continue;
				}
				if (klijent && !BookingRules.uProzoru(resurs.getPravila(), t.start(), sada, zonaId)) {
					continue;
				}
				termini.add(new OrganizationFreeSlotsResponse.Slot(resurs.getId(), resurs.getName(),
						resurs.getCapacity(), t.start(), t.end(), t.seatsLeft()));
			}
		}
		termini.sort(Comparator.comparing(OrganizationFreeSlotsResponse.Slot::start)
				.thenComparing(OrganizationFreeSlotsResponse.Slot::resourceName));
		return new OrganizationFreeSlotsResponse(
				zona != null ? zona : organizations.getReference(organizationId).getTimezone(), termini);
	}

	// Ponuđeni termini podijeljeni na slobodne i zauzete. Ovo je samo prikaz za kalendar:
	// da je termin u međuvremenu zauzet, rezervacija ga ionako odbije s 409.
	@Transactional(readOnly = true)
	public SlotCalendarResponse calendar(UUID resourceId, LocalDate from, LocalDate to, UUID userId) {
		return podijeli(resourceId, slotService.slots(resourceId, from, to), userId);
	}

	// Cijeli dan za ekran na vratima, i termin koji je u toku. Bez korisnika: nijedna rezervacija nije
	// "moja", pa zauzet termin ne kaže ni čiji je ni u kojem je statusu.
	@Transactional(readOnly = true)
	public SlotCalendarResponse wholeDay(UUID resourceId, LocalDate dan) {
		return podijeli(resourceId, slotService.wholeDay(resourceId, dan), null);
	}

	private SlotCalendarResponse podijeli(UUID resourceId, SlotsResponse ponudjeni, UUID userId) {
		if (ponudjeni.slots().isEmpty()) {
			return new SlotCalendarResponse(ponudjeni.timezone(), ponudjeni.capacity(), List.of(), List.of(),
					List.of());
		}
		Instant od = ponudjeni.slots().getFirst().start();
		Instant doKraja = ponudjeni.slots().getLast().end();
		List<Booking> drze = bookings.findByResourceIdAndStatusInAndStartTimeBeforeAndEndTimeAfter(resourceId,
				BookingStatus.DRZE_TERMIN, doKraja, od);
		// Dva upita za cijelu sedmicu, ne po terminu: ponude koje traju (bilo čije) i moji živi upisi
		List<WaitlistEntry> ponude = waitlist.findActiveOffers(resourceId, od, doKraja, clock.instant());
		List<WaitlistEntry> moji = userId == null ? List.of()
				: waitlist.findLiveForClient(resourceId, userId, od, doKraja, WaitlistStatus.ZIVI);
		List<FreeSlot> slobodni = new ArrayList<>();
		List<TakenSlot> zauzeti = new ArrayList<>();
		List<OfferedSlot> meni = new ArrayList<>();
		for (TimeRange termin : ponudjeni.slots()) {
			List<Booking> preklapaju = drze.stream()
					.filter(b -> termin.overlaps(new TimeRange(b.getStartTime(), b.getEndTime())))
					.toList();
			List<WaitlistEntry> ponudeTermina = ponude.stream()
					.filter(w -> termin.overlaps(new TimeRange(w.getStartTime(), w.getEndTime())))
					.toList();
			Optional<WaitlistEntry> meniPonudjen = ponudeTermina.stream()
					.filter(w -> userId != null && w.getClient().getId().equals(userId))
					.findFirst();
			UUID mojUpis = moji.stream()
					.filter(w -> w.getStartTime().equals(termin.start()))
					.map(WaitlistEntry::getId)
					.findFirst()
					.orElse(null);
			// Ako je admin promijenio dužinu termina, termin može dodirivati više rezervacija. Tada
			// prednost ima moja, da je ne prikažemo kao tuđu. getId() na LAZY klijentu ne učitava red.
			BookingStatus moj = preklapaju.stream()
					.filter(b -> userId != null && b.getClient().getId().equals(userId))
					.map(Booking::getStatus)
					.findFirst()
					.orElse(null);
			// Isto pravilo kao SlobodnaMjesta, samo nad redovima koji su već učitani za cijelu sedmicu
			int zaMene = SlobodnaMjesta.slobodna(preklapaju, ponudjeni.capacity()).size()
					- (ponudeTermina.size() - (meniPonudjen.isPresent() ? 1 : 0));
			if (moj == null && zaMene > 0) {
				slobodni.add(new FreeSlot(termin.start(), termin.end(), zaMene));
				// Ponuda meni: mjesto je slobodno samo za mene i samo do roka
				meniPonudjen.ifPresent(p -> meni.add(new OfferedSlot(termin.start(), termin.end(),
						p.getOfferedUntil(), p.getId())));
				continue;
			}
			zauzeti.add(new TakenSlot(termin.start(), termin.end(), moj, moj == null ? mojUpis : null));
		}
		return new SlotCalendarResponse(ponudjeni.timezone(), ponudjeni.capacity(), slobodni, zauzeti, meni);
	}

}
