package ba.slotbook.resource;

import java.time.Clock;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.audit.AuditAction;
import ba.slotbook.audit.AuditLogService;
import ba.slotbook.error.InvalidFieldException;
import ba.slotbook.organization.OrganizationRole;
import ba.slotbook.organization.OrganizationService;
import ba.slotbook.paging.PageResponse;
import ba.slotbook.user.UserRepository;

@Service
public class ResourceService {

	private final ResourceRepository resources;
	private final OrganizationService organizationService;
	private final UserRepository users;
	private final AuditLogService auditLog;
	private final Clock clock;

	public ResourceService(ResourceRepository resources, OrganizationService organizationService,
			UserRepository users, AuditLogService auditLog, Clock clock) {
		this.resources = resources;
		this.organizationService = organizationService;
		this.users = users;
		this.auditLog = auditLog;
		this.clock = clock;
	}

	// Provider je onaj ko pravi resurs. Ulogu je provjerio @PreAuthorize, a da je član baš ove
	// organizacije čuva i baza (resources_provider_clan).
	@Transactional
	public ResourceResponse create(UUID organizationId, UUID providerId, String name, String description,
			int slotMinutes, boolean autoConfirm, int cancelNoticeHours, int capacity, int priceCents) {
		Resource resource = resources.save(new Resource(organizationService.getReference(organizationId),
				users.getReferenceById(providerId), name, description, slotMinutes, autoConfirm, cancelNoticeHours,
				capacity, priceCents, clock.instant()));
		auditLog.record(organizationId, providerId, AuditAction.RESOURCE_CREATED, resource.getId(), name);
		return ResourceResponse.from(resource);
	}

	// Traži se po paru (resurs, organizacija iz putanje): resurs druge organizacije je ovdje 404,
	// i kad ga korisnik smije uređivati tamo. Izmjenu u bazu upiše Hibernate pri commitu (dirty checking).
	@Transactional
	public ResourceResponse update(UUID organizationId, UUID actorId, UUID resourceId, String name, String description,
			int slotMinutes, boolean autoConfirm, int cancelNoticeHours, int capacity, int priceCents) {
		Resource resource = resources.findByIdAndOrganizationId(resourceId, organizationId)
				.orElseThrow(ResourceNotFoundException::new);
		resource.update(name, description, slotMinutes, autoConfirm, cancelNoticeHours, capacity, priceCents);
		auditLog.record(organizationId, actorId, AuditAction.RESOURCE_UPDATED, resourceId, name);
		return ResourceResponse.from(resource);
	}

	// Postojanje i pristup je već provjerio @PreAuthorize (ResourceAccess.canView)
	@Transactional(readOnly = true)
	public ResourceResponse get(UUID resourceId) {
		return resources.findById(resourceId)
				.map(ResourceResponse::from)
				.orElseThrow(ResourceNotFoundException::new);
	}

	// Novi provider mora voditi resurse u ovoj organizaciji. Da je član, čuva i baza
	// (resources_provider_clan), ali uloga CLIENT bi tamo prošla, pa se provjerava ovdje.
	@Transactional
	public ResourceResponse changeProvider(UUID organizationId, UUID adminId, UUID resourceId, UUID providerId) {
		Resource resource = resources.findByIdAndOrganizationId(resourceId, organizationId)
				.orElseThrow(ResourceNotFoundException::new);
		boolean mozeVoditi = organizationService.findRole(organizationId, providerId)
				.map(uloga -> uloga == OrganizationRole.ADMIN || uloga == OrganizationRole.PROVIDER)
				.orElse(false);
		if (!mozeVoditi) {
			throw new InvalidFieldException("providerId",
					"Resurs može voditi samo član organizacije s ulogom PROVIDER ili ADMIN.");
		}
		resource.changeProvider(users.getReferenceById(providerId));
		auditLog.record(organizationId, adminId, AuditAction.RESOURCE_PROVIDER_CHANGED, resourceId,
				providerId.toString());
		return ResourceResponse.from(resource);
	}

	// Već deaktiviran resurs ostaje deaktiviran, pa ponovljen DELETE ne pravi grešku
	@Transactional
	public void deactivate(UUID organizationId, UUID actorId, UUID resourceId) {
		Resource resource = resources.findByIdAndOrganizationId(resourceId, organizationId)
				.orElseThrow(ResourceNotFoundException::new);
		resource.deactivate();
		auditLog.record(organizationId, actorId, AuditAction.RESOURCE_DEACTIVATED, resourceId, resource.getName());
	}

	// Redoslijed određuje server, a sort iz zahtjeva se ignoriše: ?sort=provider.passwordHash bi
	// inače sortirao po hashu tuđe lozinke. id razdvaja resurse istog naziva, da isti resurs ne
	// završi na dvije stranice ili ni na jednoj. Klijent vidi samo aktivne resurse, a admin i
	// provider i deaktivirane, da ih mogu pregledati.
	@Transactional(readOnly = true)
	public PageResponse<ResourceResponse> list(UUID organizationId, UUID userId, Pageable pageable) {
		Pageable poNazivu = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("name", "id"));
		// Članstvo postoji, to je provjerio @PreAuthorize
		Page<Resource> stranica = organizationService.findRole(organizationId, userId).orElseThrow() == OrganizationRole.CLIENT
				? resources.findByOrganizationIdAndActiveTrue(organizationId, poNazivu)
				: resources.findByOrganizationId(organizationId, poNazivu);
		return PageResponse.from(stranica.map(ResourceResponse::from));
	}

}
