package ba.slotbook.organization;

import java.util.UUID;

public record AcceptedInviteResponse(UUID organizationId, String slug, OrganizationRole role) {
}
