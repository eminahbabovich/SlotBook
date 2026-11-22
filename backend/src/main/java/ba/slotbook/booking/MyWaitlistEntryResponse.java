package ba.slotbook.booking;

import java.time.Instant;
import java.util.UUID;

// Slug i resourceId su tu da frontend napravi link na kalendar, gdje se ponuđeni termin rezerviše
public record MyWaitlistEntryResponse(UUID id, UUID resourceId, String resourceName, String organizationName,
		String organizationSlug, String timezone, Instant startTime, Instant endTime, WaitlistStatus status,
		Instant offeredUntil) {

	static MyWaitlistEntryResponse from(WaitlistEntry upis) {
		var resurs = upis.getResource();
		var organizacija = resurs.getOrganization();
		return new MyWaitlistEntryResponse(upis.getId(), resurs.getId(), resurs.getName(), organizacija.getName(),
				organizacija.getSlug(), organizacija.getTimezone(), upis.getStartTime(), upis.getEndTime(),
				upis.getStatus(), upis.getOfferedUntil());
	}

}
