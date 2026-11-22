package ba.slotbook.booking;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.availability.SlotService;
import ba.slotbook.availability.SlotsResponse;
import ba.slotbook.availability.TimeRange;
import ba.slotbook.booking.SlotCalendarResponse.OfferedSlot;
import ba.slotbook.booking.SlotCalendarResponse.TakenSlot;

@Service
public class FreeSlotService {

	private final SlotService slotService;
	private final BookingRepository bookings;
	private final WaitlistEntryRepository waitlist;
	private final Clock clock;

	public FreeSlotService(SlotService slotService, BookingRepository bookings, WaitlistEntryRepository waitlist,
			Clock clock) {
		this.slotService = slotService;
		this.bookings = bookings;
		this.waitlist = waitlist;
		this.clock = clock;
	}

	// Ponuđeni termini podijeljeni na slobodne i zauzete. Ovo je samo prikaz za kalendar:
	// da je termin u međuvremenu zauzet, rezervacija ga ionako odbije s 409.
	@Transactional(readOnly = true)
	public SlotCalendarResponse calendar(UUID resourceId, LocalDate from, LocalDate to, UUID userId) {
		SlotsResponse ponudjeni = slotService.slots(resourceId, from, to);
		if (ponudjeni.slots().isEmpty()) {
			return new SlotCalendarResponse(ponudjeni.timezone(), List.of(), List.of(), List.of());
		}
		Instant od = ponudjeni.slots().getFirst().start();
		Instant doKraja = ponudjeni.slots().getLast().end();
		List<Booking> drze = bookings.findByResourceIdAndStatusInAndStartTimeBeforeAndEndTimeAfter(resourceId,
				BookingStatus.DRZE_TERMIN, doKraja, od);
		// Dva upita za cijelu sedmicu, ne po terminu: ponude koje traju (bilo čije) i moji živi upisi
		List<WaitlistEntry> ponude = waitlist.findActiveOffers(resourceId, od, doKraja, clock.instant());
		List<WaitlistEntry> moji = waitlist.findLiveForClient(resourceId, userId, od, doKraja, WaitlistStatus.ZIVI);
		List<TimeRange> slobodni = new ArrayList<>();
		List<TakenSlot> zauzeti = new ArrayList<>();
		List<OfferedSlot> meni = new ArrayList<>();
		for (TimeRange termin : ponudjeni.slots()) {
			List<Booking> preklapaju = drze.stream()
					.filter(b -> termin.overlaps(new TimeRange(b.getStartTime(), b.getEndTime())))
					.toList();
			Optional<WaitlistEntry> ponuda = ponude.stream()
					.filter(w -> termin.overlaps(new TimeRange(w.getStartTime(), w.getEndTime())))
					.findFirst();
			UUID mojUpis = moji.stream()
					.filter(w -> w.getStartTime().equals(termin.start()))
					.map(WaitlistEntry::getId)
					.findFirst()
					.orElse(null);
			if (preklapaju.isEmpty() && ponuda.isEmpty()) {
				slobodni.add(termin);
				continue;
			}
			// Ponuda meni: termin je slobodan, ali samo za mene i samo do roka
			if (preklapaju.isEmpty() && ponuda.get().getClient().getId().equals(userId)) {
				slobodni.add(termin);
				meni.add(new OfferedSlot(termin.start(), termin.end(), ponuda.get().getOfferedUntil(),
						ponuda.get().getId()));
				continue;
			}
			// Ako je admin promijenio dužinu termina, termin može dodirivati više rezervacija. Tada
			// prednost ima moja, da je ne prikažemo kao tuđu. getId() na LAZY klijentu ne učitava red.
			BookingStatus moj = preklapaju.stream()
					.filter(b -> b.getClient().getId().equals(userId))
					.map(Booking::getStatus)
					.findFirst()
					.orElse(null);
			zauzeti.add(new TakenSlot(termin.start(), termin.end(), moj, moj == null ? mojUpis : null));
		}
		return new SlotCalendarResponse(ponudjeni.timezone(), slobodni, zauzeti, meni);
	}

}
