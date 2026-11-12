package ba.slotbook.organization;

import jakarta.validation.constraints.NotBlank;

public record AcceptInviteRequest(@NotBlank(message = "Nedostaje token pozivnice.") String token) {
}
