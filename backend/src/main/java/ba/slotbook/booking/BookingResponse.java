package ba.slotbook.booking;

import java.time.Instant;
import java.util.UUID;

public record BookingResponse(UUID id, UUID resourceId, Instant startTime, Instant endTime, BookingStatus status) {

	static BookingResponse from(Booking booking) {
		return new BookingResponse(booking.getId(), booking.getResource().getId(), booking.getStartTime(),
				booking.getEndTime(), booking.getStatus());
	}

}
