package ba.slotbook.booking;

import java.time.Instant;
import java.util.UUID;

// Blokada zbog nedolazaka u jednoj organizaciji, za traku u Mojim rezervacijama
public record BlockResponse(UUID organizationId, String organizationName, String timezone, Instant blockedUntil,
		long noShows) {
}
