package ba.slotbook.booking;

import java.time.Instant;
import java.util.UUID;

// Klijent ima rezervacije u više organizacija, pa uz svaku ide naziv resursa i zona u kojoj se
// vrijeme prikazuje. Bez toga bi frontend za svaki red slao još jedan zahtjev.
public record MyBookingResponse(UUID id, UUID resourceId, String resourceName, String timezone, Instant startTime,
		Instant endTime, BookingStatus status) {

	static MyBookingResponse from(Booking booking) {
		return new MyBookingResponse(booking.getId(), booking.getResource().getId(), booking.getResource().getName(),
				booking.getResource().getOrganization().getTimezone(), booking.getStartTime(), booking.getEndTime(),
				booking.getStatus());
	}

}
