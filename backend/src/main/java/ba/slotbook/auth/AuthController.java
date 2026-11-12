package ba.slotbook.auth;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ba.slotbook.user.UserResponse;
import ba.slotbook.user.UserService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	static final String REFRESH_KOLACIC = "refresh_token";

	private final UserService userService;
	private final AuthService authService;

	public AuthController(UserService userService, AuthService authService) {
		this.userService = userService;
		this.authService = authService;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse register(@Valid @RequestBody RegisterRequest request) {
		return userService.register(request.email(), request.password(), request.displayName());
	}

	@PostMapping("/login")
	public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
		return saKolacicem(authService.login(request.email(), request.password()));
	}

	@PostMapping("/refresh")
	public ResponseEntity<TokenResponse> refresh(
			@CookieValue(name = REFRESH_KOLACIC, required = false) String refreshToken) {
		return saKolacicem(authService.refresh(refreshToken));
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_KOLACIC, required = false) String refreshToken) {
		authService.logout(refreshToken);
		return ResponseEntity.noContent()
				.header(HttpHeaders.SET_COOKIE, kolacic("", Duration.ZERO).toString())
				.build();
	}

	private static ResponseEntity<TokenResponse> saKolacicem(AuthService.Tokens tokens) {
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, kolacic(tokens.refreshToken(), RefreshTokenService.TRAJE).toString())
				.body(tokens.access());
	}

	// HttpOnly: JavaScript ga ne može pročitati, pa ga ni XSS ne može ukrasti.
	// Path: browser ga šalje samo na /api/auth/*, a ne uz svaki zahtjev.
	private static ResponseCookie kolacic(String vrijednost, Duration traje) {
		return ResponseCookie.from(REFRESH_KOLACIC, vrijednost)
				.httpOnly(true)
				.secure(true)
				.sameSite("Strict")
				.path("/api/auth")
				.maxAge(traje)
				.build();
	}

}
