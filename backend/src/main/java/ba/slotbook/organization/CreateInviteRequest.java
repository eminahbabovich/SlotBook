package ba.slotbook.organization;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateInviteRequest(
		@NotBlank(message = "Unesi email osobe koju pozivaš.")
		@Email(message = "Email nije ispravan.")
		@Size(max = 254, message = "Email može imati najviše 254 znaka.")
		String email,

		@NotNull(message = "Izaberi ulogu: ADMIN, PROVIDER ili CLIENT.")
		OrganizationRole role) {
}
