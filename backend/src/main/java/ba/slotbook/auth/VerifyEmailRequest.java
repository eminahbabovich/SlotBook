package ba.slotbook.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Token ide u tijelo, ne u putanju, da ne ostane u logovima servera (kao kod pozivnica)
public record VerifyEmailRequest(@NotBlank @Size(max = 100) String token) {
}
