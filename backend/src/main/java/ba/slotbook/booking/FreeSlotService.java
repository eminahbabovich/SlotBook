package ba.slotbook.booking;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.availability.SlotService;
import ba.slotbook.availability.SlotsResponse;
import ba.slotbook.availability.TimeRange;

@Service
public class FreeSlotService {

	private final SlotService slotService;
	private final BookingRepository bookings;

	public FreeSlotService(SlotService slotService, BookingRepository bookings) {
		this.slotService = slotService;
		this.bookings = bookings;
	}

	// Ponuđeni termini bez onih koje drži aktivna rezervacija. Ovo je samo prikaz za kalendar:
	// da je termin u međuvremenu zauzet, rezervacija ga ionako odbije s 409.
	@Transactional(readOnly = true)
	public SlotsResponse freeSlots(UUID resourceId, LocalDate from, LocalDate to) {
		SlotsResponse ponudjeni = slotService.slots(resourceId, from, to);
		if (ponudjeni.slots().isEmpty()) {
			return ponudjeni;
		}
		List<TimeRange> zauzeti = bookings.findByResourceIdAndStatusInAndStartTimeBeforeAndEndTimeAfter(resourceId,
				BookingStatus.DRZE_TERMIN, ponudjeni.slots().getLast().end(), ponudjeni.slots().getFirst().start())
				.stream()
				.map(b -> new TimeRange(b.getStartTime(), b.getEndTime()))
				.toList();
		return new SlotsResponse(ponudjeni.timezone(), ponudjeni.slots().stream()
				.filter(termin -> zauzeti.stream().noneMatch(termin::overlaps))
				.toList());
	}

}
