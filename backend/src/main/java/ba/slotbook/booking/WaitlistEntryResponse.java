package ba.slotbook.booking;

import java.time.Instant;
import java.util.UUID;

// offeredUntil je null dok termin nije ponuđen
public record WaitlistEntryResponse(UUID id, UUID resourceId, Instant startTime, Instant endTime,
		WaitlistStatus status, Instant offeredUntil) {

	static WaitlistEntryResponse from(WaitlistEntry upis) {
		return new WaitlistEntryResponse(upis.getId(), upis.getResource().getId(), upis.getStartTime(),
				upis.getEndTime(), upis.getStatus(), upis.getOfferedUntil());
	}

}
