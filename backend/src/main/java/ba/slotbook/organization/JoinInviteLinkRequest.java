package ba.slotbook.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinInviteLinkRequest(@NotBlank @Size(max = 100) String token) {
}
