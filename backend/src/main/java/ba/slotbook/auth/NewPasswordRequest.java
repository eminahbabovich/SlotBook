package ba.slotbook.auth;

import ba.slotbook.user.MaxUtf8Bytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Ista pravila za lozinku kao kod registracije
public record NewPasswordRequest(
		@NotBlank @Size(max = 100)
		String token,

		@NotBlank(message = "Unesi novu lozinku.")
		@Size(min = 8, message = "Lozinka treba imati najmanje 8 znakova.")
		@MaxUtf8Bytes(value = 72, message = "Lozinka je preduga. Skrati je, slova č, ć, š, đ i ž se računaju duplo.")
		String password) {
}
