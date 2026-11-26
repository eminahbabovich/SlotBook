package ba.slotbook.organization;

import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull(message = "Izaberi ulogu.") OrganizationRole role) {
}
