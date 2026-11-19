package ba.slotbook.auth;

import org.springframework.http.HttpStatus;
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

	// Uvijek 204, da se po odgovoru ne vidi ima li email nalog i je li potvrđen
	@PostMapping("/api/auth/email-verification")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void resend(@Valid @RequestBody NewVerificationLinkRequest request) {
		verification.resend(request.email());
	}

}
