package ba.slotbook.organization;

import java.time.Instant;
import java.util.UUID;

// url ima i token, pa ga vidi samo admin (GET i POST su iza @PreAuthorize ... 'ADMIN')
public record InviteLinkResponse(UUID id, String url, int maxUses, int uses, Instant expiresAt, Instant revokedAt,
		Instant createdAt, boolean active) {
}
