package ba.slotbook.organization;

import java.time.Instant;
import java.util.UUID;

import ba.slotbook.user.User;

public record MemberResponse(UUID userId, String displayName, String email, OrganizationRole role,
		Instant joinedAt) {

	static MemberResponse from(OrganizationMember member) {
		User user = member.getUser();
		return new MemberResponse(user.getId(), user.getDisplayName(), user.getEmail(), member.getRole(),
				member.getJoinedAt());
	}

}
