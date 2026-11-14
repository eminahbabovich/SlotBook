package ba.slotbook.organization;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.auth.RandomToken;
import ba.slotbook.notification.EmailContent;
import ba.slotbook.notification.NotificationService;
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
	private final NotificationService notifications;
	private final Clock clock;
	private final String appUrl;

	public OrganizationInviteService(OrganizationInviteRepository invites, OrganizationMemberRepository members,
			OrganizationRepository organizations, UserRepository users, NotificationService notifications,
			Clock clock, @Value("${slotbook.app-url}") String appUrl) {
		this.invites = invites;
		this.members = members;
		this.organizations = organizations;
		this.users = users;
		this.notifications = notifications;
		this.clock = clock;
		this.appUrl = appUrl;
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
		Organization organizacija = organizations.getReferenceById(organizationId);
		OrganizationInvite invite;
		try {
			invite = invites.saveAndFlush(new OrganizationInvite(organizacija, normalizovan, role,
					RandomToken.sha256(token), sada, sada.plus(TRAJE)));
		} catch (DataIntegrityViolationException e) {
			// Dva admina u istom trenutku: oba obrišu staru, oba upisuju, a indeks pusti samo prvog
			if (e.getMostSpecificCause().getMessage().contains("organization_invites_jedna_otvorena")) {
				throw new InviteRaceException();
			}
			throw e;
		}
		notifications.enqueueInvite(normalizovan, invite.getId(),
				mailPozivnice(organizacija.getName(), role, appUrl + "/pozivnica#token=" + token));
		return InviteResponse.from(invite);
	}

	private static EmailContent mailPozivnice(String organizacija, OrganizationRole role, String link) {
		String uloga = switch (role) {
			case ADMIN -> "admin";
			case PROVIDER -> "provider";
			case CLIENT -> "klijent";
		};
		return new EmailContent("Pozivnica na SlotBook: " + organizacija,
				"Zdravo,\n\n" + organizacija + " te poziva na SlotBook, s ulogom: " + uloga + ".\n\n"
						+ "Da prihvatiš pozivnicu, otvori link i prijavi se ili napravi nalog s ovim emailom:\n"
						+ link + "\n\nPozivnica važi 7 dana. Ako ne znaš o čemu se radi, zanemari ovaj mail.");
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
