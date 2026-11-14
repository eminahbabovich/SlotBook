package ba.slotbook.booking;

import java.time.Instant;

// Ko je promijenio status vidi se po imenu, ne po ID-ju korisnika
public record BookingEventResponse(BookingStatus fromStatus, BookingStatus toStatus, String actorName,
		Instant createdAt) {

	static BookingEventResponse from(BookingEvent event) {
		return new BookingEventResponse(event.getFromStatus(), event.getToStatus(), event.getActor().getDisplayName(),
				event.getCreatedAt());
	}

}
