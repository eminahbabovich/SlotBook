package ba.slotbook.auth;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
public class EmailVerificationController {

	private final EmailVerificationService verification;

	public EmailVerificationController(EmailVerificationService verification) {
		this.verification = verification;
	}

	// Bez prijave: link se može otvoriti i na telefonu gdje korisnik nije prijavljen
	@PostMapping("/api/auth/verify-email")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void verify(@Valid @RequestBody VerifyEmailRequest request) {
		verification.verify(request.token());
	}

	// Samo za sebe: ID je iz tokena. Već potvrđen email ne dobija novi mail, a odgovor je isti.
	@PostMapping("/api/me/email-verification")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void resend(@AuthenticationPrincipal Jwt token) {
		verification.resend(UUID.fromString(token.getSubject()));
	}

}
