package ba.slotbook.organization;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.auth.RandomToken;
import ba.slotbook.organization.InviteNotUsableException.Reason;
import ba.slotbook.user.User;
import ba.slotbook.user.UserRepository;

@Service
public class OrganizationInviteService {

	static final Duration TRAJE = Duration.ofDays(7);

	private final OrganizationInviteRepository invites;
	private final OrganizationMemberRepository members;
	private final OrganizationRepository organizations;
	private final UserRepository users;
	private final Clock clock;

	public OrganizationInviteService(OrganizationInviteRepository invites, OrganizationMemberRepository members,
			OrganizationRepository organizations, UserRepository users, Clock clock) {
		this.invites = invites;
		this.members = members;
		this.organizations = organizations;
		this.users = users;
		this.clock = clock;
	}

	// Nova pozivnica za isti email zamjenjuje staru neprihvaćenu: novi token, nova uloga, novi rok
	@Transactional
	public InviteResponse invite(UUID organizationId, String email, OrganizationRole role) {
		String normalizovan = User.normalizeEmail(email);
		if (members.existsByIdOrganizationIdAndUserEmail(organizationId, normalizovan)) {
			throw new AlreadyMemberException();
		}
		invites.deleteOpen(organizationId, normalizovan);

		String token = RandomToken.generate();
		Instant sada = clock.instant();
		OrganizationInvite invite = invites.save(new OrganizationInvite(organizations.getReferenceById(organizationId),
				normalizovan, role, RandomToken.sha256(token), sada, sada.plus(TRAJE)));
		return InviteResponse.from(invite, token);
	}

	// Ako upis članstva padne, rollback vrati i accepted_at, pa pozivnica ostaje upotrebljiva
	@Transactional
	public AcceptedInviteResponse accept(UUID userId, String token) {
		OrganizationInvite invite = invites.findByTokenHash(RandomToken.sha256(token))
				.orElseThrow(() -> new InviteNotUsableException(Reason.NOT_FOUND));
		User user = users.findById(userId).orElseThrow();
		if (!user.getEmail().equals(invite.getEmail())) {
			throw new InviteNotUsableException(Reason.OTHER_EMAIL);
		}
		Instant sada = clock.instant();
		if (invite.isExpired(sada)) {
			throw new InviteNotUsableException(Reason.EXPIRED);
		}
		if (invites.markAccepted(invite.getId(), sada) == 0) {
			throw new InviteNotUsableException(Reason.ALREADY_USED);
		}

		Organization organization = invite.getOrganization();
		if (members.existsById(new OrganizationMember.Id(organization.getId(), userId))) {
			throw new AlreadyMemberException();
		}
		members.save(new OrganizationMember(organization, user, invite.getRole(), sada));
		return new AcceptedInviteResponse(organization.getId(), organization.getSlug(), invite.getRole());
	}

}
