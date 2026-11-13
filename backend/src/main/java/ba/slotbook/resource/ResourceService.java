package ba.slotbook.resource;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.organization.OrganizationService;
import ba.slotbook.user.UserRepository;

@Service
public class ResourceService {

	private final ResourceRepository resources;
	private final OrganizationService organizationService;
	private final UserRepository users;
	private final Clock clock;

	public ResourceService(ResourceRepository resources, OrganizationService organizationService,
			UserRepository users, Clock clock) {
		this.resources = resources;
		this.organizationService = organizationService;
		this.users = users;
		this.clock = clock;
	}

	// Provider je onaj ko pravi resurs. Ulogu je provjerio @PreAuthorize, a da je član baš ove
	// organizacije čuva i baza (resources_provider_clan).
	@Transactional
	public ResourceResponse create(UUID organizationId, UUID providerId, String name, String description,
			int slotMinutes) {
		Resource resource = resources.save(new Resource(organizationService.getReference(organizationId),
				users.getReferenceById(providerId), name, description, slotMinutes, clock.instant()));
		return ResourceResponse.from(resource);
	}

}
