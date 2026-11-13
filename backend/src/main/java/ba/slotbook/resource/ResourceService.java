package ba.slotbook.resource;

import java.time.Clock;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.organization.OrganizationService;
import ba.slotbook.paging.PageResponse;
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

	// Traži se po paru (resurs, organizacija iz putanje): resurs druge organizacije je ovdje 404,
	// i kad ga korisnik smije uređivati tamo. Izmjenu u bazu upiše Hibernate pri commitu (dirty checking).
	@Transactional
	public ResourceResponse update(UUID organizationId, UUID resourceId, String name, String description,
			int slotMinutes) {
		Resource resource = resources.findByIdAndOrganizationId(resourceId, organizationId)
				.orElseThrow(ResourceNotFoundException::new);
		resource.update(name, description, slotMinutes);
		return ResourceResponse.from(resource);
	}

	// Redoslijed određuje server, a sort iz zahtjeva se ignoriše: ?sort=provider.passwordHash bi
	// inače sortirao po hashu tuđe lozinke. id razdvaja resurse istog naziva, da isti resurs ne
	// završi na dvije stranice ili ni na jednoj.
	@Transactional(readOnly = true)
	public PageResponse<ResourceResponse> list(UUID organizationId, Pageable pageable) {
		Pageable poNazivu = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("name", "id"));
		return PageResponse.from(resources.findByOrganizationId(organizationId, poNazivu).map(ResourceResponse::from));
	}

}
