package ba.slotbook.organization;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.audit.AuditAction;
import ba.slotbook.audit.AuditLogService;
import ba.slotbook.paging.PageResponse;
import ba.slotbook.user.User;
import ba.slotbook.user.UserRepository;

@Service
public class OrganizationService {

	private final OrganizationRepository organizations;
	private final OrganizationMemberRepository members;
	private final UserRepository users;
	private final AuditLogService auditLog;
	private final ApplicationEventPublisher objave;
	private final Clock clock;

	public OrganizationService(OrganizationRepository organizations, OrganizationMemberRepository members,
			UserRepository users, AuditLogService auditLog, ApplicationEventPublisher objave, Clock clock) {
		this.organizations = organizations;
		this.members = members;
		this.users = users;
		this.auditLog = auditLog;
		this.objave = objave;
		this.clock = clock;
	}

	// Organizacija bez admina ne bi imala ko da je uređuje, pa oba upisa uspiju ili nijedan
	@Transactional
	public OrganizationResponse create(UUID creatorId, String name, String slug, String timezone) {
		Instant sada = clock.instant();
		Organization organization;
		try {
			organization = organizations.saveAndFlush(new Organization(name, slug, timezone, sada));
		} catch (DataIntegrityViolationException e) {
			throw new SlugAlreadyUsedException(slug);
		}

		// Referenca bez SELECT-a: za upis članstva treba samo ID korisnika, ne cijeli red iz baze
		User creator = users.getReferenceById(creatorId);
		members.save(new OrganizationMember(organization, creator, OrganizationRole.ADMIN, sada));
		auditLog.record(organization.getId(), creatorId, AuditAction.ORGANIZATION_CREATED, organization.getId(), null);

		return OrganizationResponse.from(organization);
	}

	// Drugi paketi dobijaju organizaciju preko servisa, a repozitorij ostaje unutar paketa.
	// Referenca bez SELECT-a: pozivalac je već prošao @PreAuthorize, pa organizacija postoji.
	public Organization getReference(UUID organizationId) {
		return organizations.getReferenceById(organizationId);
	}

	// Prazno kad korisnik nije član organizacije
	@Transactional(readOnly = true)
	public Optional<OrganizationRole> findRole(UUID organizationId, UUID userId) {
		return members.findById(new OrganizationMember.Id(organizationId, userId)).map(OrganizationMember::getRole);
	}

	@Transactional(readOnly = true)
	public OrganizationResponse getBySlug(String slug) {
		return organizations.findBySlug(slug)
				.map(OrganizationResponse::from)
				.orElseThrow(() -> new OrganizationNotFoundException(slug));
	}

	// Bez stranica: korisnik je član nekoliko organizacija, a frontendu treba cijela lista
	// da odluči kuda vodi poslije prijave.
	@Transactional(readOnly = true)
	public List<MyOrganizationResponse> listForUser(UUID userId) {
		return members.findAllByUserWithOrganization(userId).stream().map(MyOrganizationResponse::from).toList();
	}

	// Admin ne smije ostati bez admina, a član koji vodi resurse ne smije postati klijent: klijent ne
	// vodi resurse, a resurs bez nekog ko ga vodi nema ko potvrditi rezervacije.
	@Transactional
	public MemberResponse changeRole(UUID organizationId, UUID userId, OrganizationRole nova, UUID adminId) {
		organizations.findLockedById(organizationId).orElseThrow();
		OrganizationMember clan = members.findById(new OrganizationMember.Id(organizationId, userId))
				.orElseThrow(MemberNotFoundException::new);
		OrganizationRole stara = clan.getRole();
		if (stara == nova) {
			return MemberResponse.from(clan);
		}
		if (stara == OrganizationRole.ADMIN && members.countByIdOrganizationIdAndRole(organizationId,
				OrganizationRole.ADMIN) == 1) {
			throw new LastAdminException();
		}
		if (nova == OrganizationRole.CLIENT) {
			List<String> resursi = members.resursiKojeVodi(organizationId, userId);
			if (!resursi.isEmpty()) {
				throw new MemberLeadsResourcesException(resursi);
			}
		}
		clan.promijeniUlogu(nova);
		auditLog.record(organizationId, adminId, AuditAction.MEMBER_ROLE_CHANGED, userId, stara + " -> " + nova);
		return MemberResponse.from(clan);
	}

	// Lock organizacije: dva admina koja u isto vrijeme uklanjaju jedan drugog idu jedan za drugim. Bez
	// toga bi oba vidjela "ima još jedan admin" i organizacija bi ostala bez ijednog.
	@Transactional
	public void removeMember(UUID organizationId, UUID userId, UUID adminId) {
		organizations.findLockedById(organizationId).orElseThrow();
		OrganizationMember clan = members.findById(new OrganizationMember.Id(organizationId, userId))
				.orElseThrow(MemberNotFoundException::new);
		if (clan.getRole() == OrganizationRole.ADMIN
				&& members.countByIdOrganizationIdAndRole(organizationId, OrganizationRole.ADMIN) == 1) {
			throw new LastAdminException();
		}
		// I baza bi odbila brisanje (strani ključ resursa na članstvo), ali tek kao 500 bez spiska
		List<String> resursi = members.resursiKojeVodi(organizationId, userId);
		if (!resursi.isEmpty()) {
			throw new MemberLeadsResourcesException(resursi);
		}
		objave.publishEvent(new MemberRemoved(organizationId, userId, adminId));
		members.delete(clan);
		auditLog.record(organizationId, adminId, AuditAction.MEMBER_REMOVED, userId, clan.getRole().name());
	}

	// Kao kod resursa: redoslijed određuje server, a userId razdvaja članove koji su ušli u istoj sekundi
	@Transactional(readOnly = true)
	public PageResponse<MemberResponse> listMembers(UUID organizationId, Pageable pageable) {
		Pageable poDolasku = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
				Sort.by("joinedAt", "id.userId"));
		return PageResponse.from(members.findAllWithUser(organizationId, poDolasku).map(MemberResponse::from));
	}

}
