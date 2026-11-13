package ba.slotbook.resource;

import java.util.UUID;

public record ResourceResponse(UUID id, UUID organizationId, UUID providerId, String name, String description,
		int slotMinutes, boolean active) {

	// getId() na LAZY referenci ne učitava red, Hibernate ID već zna
	static ResourceResponse from(Resource resource) {
		return new ResourceResponse(resource.getId(), resource.getOrganization().getId(),
				resource.getProvider().getId(), resource.getName(), resource.getDescription(),
				resource.getSlotMinutes(), resource.isActive());
	}

}
