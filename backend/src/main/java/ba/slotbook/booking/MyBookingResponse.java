package ba.slotbook.booking;

import java.time.Instant;
import java.util.UUID;

// Klijent ima rezervacije u više organizacija, pa uz svaku ide naziv resursa i organizacije i zona
// u kojoj se vrijeme prikazuje. Bez toga bi frontend za svaki red slao još jedan zahtjev.
// cancelDeadline: do kada klijent može sam otkazati. Za završena stanja ne znači ništa.
public record MyBookingResponse(UUID id, UUID resourceId, String resourceName, String organizationName,
		String timezone, Instant startTime, Instant endTime, BookingStatus status, Instant cancelDeadline) {

	static MyBookingResponse from(Booking booking) {
		return new MyBookingResponse(booking.getId(), booking.getResource().getId(), booking.getResource().getName(),
				booking.getResource().getOrganization().getName(), booking.getResource().getOrganization().getTimezone(),
				booking.getStartTime(), booking.getEndTime(), booking.getStatus(), booking.rokZaOtkazivanje());
	}

}
