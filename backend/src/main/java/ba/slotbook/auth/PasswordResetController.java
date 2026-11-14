package ba.slotbook.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

// Oba bez prijave: ko je zaboravio lozinku, ne može se prijaviti
@RestController
@RequestMapping("/api/auth/password-reset")
public class PasswordResetController {

	private final PasswordResetService passwordReset;

	public PasswordResetController(PasswordResetService passwordReset) {
		this.passwordReset = passwordReset;
	}

	// Uvijek 204, i za email bez naloga
	@PostMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void request(@Valid @RequestBody PasswordResetRequest request) {
		passwordReset.request(request.email());
	}

	@PostMapping("/confirm")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void confirm(@Valid @RequestBody NewPasswordRequest request) {
		passwordReset.reset(request.token(), request.password());
	}

}
