package ba.slotbook.organization;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateInviteLinkRequest(
		@NotNull(message = "Upiši koliko ljudi najviše može ući preko linka.")
		@Min(value = 1, message = "Preko linka mora moći ući bar jedna osoba.")
		@Max(value = 1000, message = "Preko jednog linka može ući najviše 1000 ljudi.")
		Integer maxUses,

		@NotNull(message = "Upiši koliko dana link važi.")
		@Min(value = 1, message = "Link mora važiti bar jedan dan.")
		@Max(value = 90, message = "Link može važiti najviše 90 dana. Poslije napravi novi.")
		Integer days) {
}
