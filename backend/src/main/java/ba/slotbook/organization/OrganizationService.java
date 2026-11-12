package ba.slotbook.organization;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.user.User;
import ba.slotbook.user.UserRepository;

@Service
public class OrganizationService {

	private final OrganizationRepository organizations;
	private final OrganizationMemberRepository members;
	private final UserRepository users;
	private final Clock clock;

	public OrganizationService(OrganizationRepository organizations, OrganizationMemberRepository members,
			UserRepository users, Clock clock) {
		this.organizations = organizations;
		this.members = members;
		this.users = users;
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

		return OrganizationResponse.from(organization);
	}

	@Transactional(readOnly = true)
	public OrganizationResponse getBySlug(String slug) {
		return organizations.findBySlug(slug)
				.map(OrganizationResponse::from)
				.orElseThrow(() -> new OrganizationNotFoundException(slug));
	}

	@Transactional(readOnly = true)
	public List<MemberResponse> listMembers(UUID organizationId) {
		return members.findAllWithUser(organizationId).stream()
				.map(MemberResponse::from)
				.toList();
	}

}
