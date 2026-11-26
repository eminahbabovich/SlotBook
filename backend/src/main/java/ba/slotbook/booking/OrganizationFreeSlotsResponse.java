package ba.slotbook.booking;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// "Nađi slobodno": slobodni termini svih aktivnih resursa organizacije, poredani po početku
public record OrganizationFreeSlotsResponse(String timezone, List<Slot> slots) {

	public record Slot(UUID resourceId, String resourceName, int capacity, Instant start, Instant end,
			int seatsLeft) {
	}

}
