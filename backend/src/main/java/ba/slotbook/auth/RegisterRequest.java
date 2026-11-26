package ba.slotbook.auth;

import ba.slotbook.user.JakaLozinka;
import ba.slotbook.user.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank(message = "Unesi email.")
		@Email(message = "Email nije ispravan.")
		@Size(max = 254, message = "Email je predug.")
		String email,

		@NotBlank(message = "Unesi lozinku.")
		@JakaLozinka
		@MaxUtf8Bytes(value = 72, message = "Lozinka je preduga. Skrati je, slova č, ć, š, đ i ž se računaju duplo.")
		String password,

		@NotBlank(message = "Unesi ime koje će drugi vidjeti.")
		@Size(max = 100, message = "Ime može imati najviše 100 znakova.")
		String displayName) {
}
