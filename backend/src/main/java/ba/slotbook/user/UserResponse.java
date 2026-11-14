package ba.slotbook.user;

import java.util.UUID;

public record UserResponse(UUID id, String email, String displayName, boolean emailVerified) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.isEmailVerified());
	}

}
