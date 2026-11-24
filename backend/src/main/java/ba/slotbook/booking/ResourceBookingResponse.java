package ba.slotbook.booking;

import java.time.Instant;
import java.util.UUID;

// Raspored za admina i providera: ko je rezervisao, ne i njegov email. Ime je dovoljno da
// provider zna koga čeka, a email ne treba pokazivati šire nego što mora.
// awaitingPayment: PENDING koju potvrđuje uplata, a ne osoblje, pa raspored ne nudi "Potvrdi".
public record ResourceBookingResponse(UUID id, Instant startTime, Instant endTime, BookingStatus status,
		String clientName, Instant expiresAt, boolean awaitingPayment) {

	static ResourceBookingResponse from(Booking booking) {
		return new ResourceBookingResponse(booking.getId(), booking.getStartTime(), booking.getEndTime(),
				booking.getStatus(), booking.getClient().getDisplayName(), booking.getExpiresAt(),
				booking.cekaUplatu());
	}

}
