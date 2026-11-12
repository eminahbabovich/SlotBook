package ba.slotbook.organization;

import java.time.Instant;
import java.util.UUID;

// token je ovdje samo do Faze 5: admin ga sam proslijedi, a kasnije stiže samo emailom
public record InviteResponse(UUID id, String email, OrganizationRole role, Instant expiresAt, String token) {

	static InviteResponse from(OrganizationInvite invite, String token) {
		return new InviteResponse(invite.getId(), invite.getEmail(), invite.getRole(), invite.getExpiresAt(), token);
	}

}
