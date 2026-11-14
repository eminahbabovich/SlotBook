package ba.slotbook.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
		@NotBlank(message = "Unesi email.")
		@Email(message = "Email nije ispravan.")
		@Size(max = 254, message = "Email je predug.")
		String email) {
}
