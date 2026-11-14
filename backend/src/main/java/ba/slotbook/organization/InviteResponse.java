package ba.slotbook.organization;

import java.time.Instant;
import java.util.UUID;

// Bez tokena: stiže samo pozvanoj osobi, emailom. Admin ga ne vidi, pa ne može prihvatiti pozivnicu umjesto nje.
public record InviteResponse(UUID id, String email, OrganizationRole role, Instant expiresAt) {

	static InviteResponse from(OrganizationInvite invite) {
		return new InviteResponse(invite.getId(), invite.getEmail(), invite.getRole(), invite.getExpiresAt());
	}

}
