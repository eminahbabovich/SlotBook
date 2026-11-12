package ba.slotbook.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
		@NotBlank(message = "Unesi email.") String email,
		@NotBlank(message = "Unesi lozinku.") String password) {
}
