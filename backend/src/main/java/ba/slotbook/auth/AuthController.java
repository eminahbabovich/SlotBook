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

import ba.slotbook.user.EmailAlreadyUsedException;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	static final String REFRESH_KOLACIC = "refresh_token";

	private final EmailVerificationService verification;
	private final AuthService authService;

	public AuthController(EmailVerificationService verification, AuthService authService) {
		this.verification = verification;
		this.authService = authService;
	}

	// Uvijek 202 bez tijela, i kad email već ima nalog: šta se desilo, saznaje samo vlasnik emaila iz
	// maila. EmailAlreadyUsedException stigne samo kad dvije registracije za isti novi email idu u istom
	// trenutku, pa UNIQUE odbije drugu, a i tada je odgovor isti.
	@PostMapping("/register")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void register(@Valid @RequestBody RegisterRequest request) {
		try {
			verification.register(request.email(), request.password(), request.displayName());
		} catch (EmailAlreadyUsedException e) {
			// Nalog je upravo napravila druga registracija, i link za potvrdu ide s njom
		}
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
