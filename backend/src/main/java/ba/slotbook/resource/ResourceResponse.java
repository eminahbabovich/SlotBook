package ba.slotbook.resource;

import java.util.UUID;

public record ResourceResponse(UUID id, UUID organizationId, UUID providerId, String name, String description,
		int slotMinutes, boolean autoConfirm, int cancelNoticeHours, int capacity, int priceCents, boolean active, boolean hasImage) {

	// getId() na LAZY referenci ne učitava red, Hibernate ID već zna. Ključ slike se ne vraća,
	// to je detalj skladišta, a slika se čita preko /api/resources/{id}/image.
	static ResourceResponse from(Resource resource) {
		return new ResourceResponse(resource.getId(), resource.getOrganization().getId(),
				resource.getProvider().getId(), resource.getName(), resource.getDescription(),
				resource.getSlotMinutes(), resource.isAutoConfirm(), resource.getCancelNoticeHours(), resource.getCapacity(), resource.getPriceCents(), resource.isActive(), resource.getImageKey() != null);
	}

}
