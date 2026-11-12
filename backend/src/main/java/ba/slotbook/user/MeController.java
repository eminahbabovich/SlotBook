package ba.slotbook.user;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

	private final UserService userService;

	public MeController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/api/me")
	public UserResponse me(@AuthenticationPrincipal Jwt token) {
		return userService.getById(UUID.fromString(token.getSubject()));
	}

}
