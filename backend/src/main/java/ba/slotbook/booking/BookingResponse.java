package ba.slotbook.booking;

import java.time.Instant;
import java.util.UUID;

// expiresAt: do kada provider mora potvrditi. Poslije potvrde rok ostaje u bazi, ali klijentu više ne znači ništa.
public record BookingResponse(UUID id, UUID resourceId, Instant startTime, Instant endTime, BookingStatus status,
		Instant expiresAt) {

	static BookingResponse from(Booking booking) {
		return new BookingResponse(booking.getId(), booking.getResource().getId(), booking.getStartTime(),
				booking.getEndTime(), booking.getStatus(),
				booking.getStatus() == BookingStatus.PENDING ? booking.getExpiresAt() : null);
	}

}
