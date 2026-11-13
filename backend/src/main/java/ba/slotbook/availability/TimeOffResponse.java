package ba.slotbook.availability;

import java.time.Instant;
import java.util.UUID;

public record TimeOffResponse(UUID id, Instant startsAt, Instant endsAt, String reason) {

	static TimeOffResponse from(TimeOff timeOff) {
		return new TimeOffResponse(timeOff.getId(), timeOff.getStartsAt(), timeOff.getEndsAt(), timeOff.getReason());
	}

}
