package ba.slotbook.booking;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.availability.SlotService;
import ba.slotbook.availability.SlotsResponse;
import ba.slotbook.availability.TimeRange;
import ba.slotbook.booking.SlotCalendarResponse.TakenSlot;

@Service
public class FreeSlotService {

	private final SlotService slotService;
	private final BookingRepository bookings;

	public FreeSlotService(SlotService slotService, BookingRepository bookings) {
		this.slotService = slotService;
		this.bookings = bookings;
	}

	// Ponuđeni termini podijeljeni na slobodne i zauzete. Ovo je samo prikaz za kalendar:
	// da je termin u međuvremenu zauzet, rezervacija ga ionako odbije s 409.
	@Transactional(readOnly = true)
	public SlotCalendarResponse calendar(UUID resourceId, LocalDate from, LocalDate to, UUID userId) {
		SlotsResponse ponudjeni = slotService.slots(resourceId, from, to);
		if (ponudjeni.slots().isEmpty()) {
			return new SlotCalendarResponse(ponudjeni.timezone(), List.of(), List.of());
		}
		List<Booking> drze = bookings.findByResourceIdAndStatusInAndStartTimeBeforeAndEndTimeAfter(resourceId,
				BookingStatus.DRZE_TERMIN, ponudjeni.slots().getLast().end(), ponudjeni.slots().getFirst().start());
		List<TimeRange> slobodni = new ArrayList<>();
		List<TakenSlot> zauzeti = new ArrayList<>();
		for (TimeRange termin : ponudjeni.slots()) {
			List<Booking> preklapaju = drze.stream()
					.filter(b -> termin.overlaps(new TimeRange(b.getStartTime(), b.getEndTime())))
					.toList();
			if (preklapaju.isEmpty()) {
				slobodni.add(termin);
				continue;
			}
			// Ako je admin promijenio dužinu termina, termin može dodirivati više rezervacija. Tada
			// prednost ima moja, da je ne prikažemo kao tuđu. getId() na LAZY klijentu ne učitava red.
			BookingStatus moj = preklapaju.stream()
					.filter(b -> b.getClient().getId().equals(userId))
					.map(Booking::getStatus)
					.findFirst()
					.orElse(null);
			zauzeti.add(new TakenSlot(termin.start(), termin.end(), moj));
		}
		return new SlotCalendarResponse(ponudjeni.timezone(), slobodni, zauzeti);
	}

}
