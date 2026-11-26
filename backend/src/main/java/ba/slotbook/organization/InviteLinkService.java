package ba.slotbook.organization;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.audit.AuditAction;
import ba.slotbook.audit.AuditLogService;
import ba.slotbook.auth.LinkToken;
import ba.slotbook.organization.InviteLinkNotUsableException.Reason;
import ba.slotbook.paging.PageResponse;
import ba.slotbook.user.User;
import ba.slotbook.user.UserRepository;

@Service
public class InviteLinkService {

	private final InviteLinkRepository links;
	private final OrganizationMemberRepository members;
	private final OrganizationRepository organizations;
	private final UserRepository users;
	private final AuditLogService auditLog;
	private final LinkToken linkToken;
	private final Clock clock;
	private final String appUrl;

	public InviteLinkService(InviteLinkRepository links, OrganizationMemberRepository members,
			OrganizationRepository organizations, UserRepository users, AuditLogService auditLog, LinkToken linkToken,
			Clock clock, @Value("${slotbook.app-url}") String appUrl) {
		this.links = links;
		this.members = members;
		this.organizations = organizations;
		this.users = users;
		this.auditLog = auditLog;
		this.linkToken = linkToken;
		this.clock = clock;
		this.appUrl = appUrl;
	}

	@Transactional
	public InviteLinkResponse create(UUID organizationId, UUID adminId, int maxUses, int days) {
		Instant sada = clock.instant();
		UUID id = UUID.randomUUID();
		String tokenHash = LinkToken.hash(linkToken.za(LinkToken.Svrha.POZIVNI_LINK, id));
		InviteLink link = links.save(new InviteLink(id, organizations.getReferenceById(organizationId), tokenHash,
				maxUses, sada.plus(Duration.ofDays(days)), users.getReferenceById(adminId), sada));
		auditLog.record(organizationId, adminId, AuditAction.INVITE_LINK_CREATED, id, "najviše " + maxUses);
		return odgovor(link, sada);
	}

	@Transactional(readOnly = true)
	public PageResponse<InviteLinkResponse> list(UUID organizationId, Pageable pageable) {
		Instant sada = clock.instant();
		Pageable najnovijiPrvo = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
				Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
		return PageResponse.from(links.findByOrganizationId(organizationId, najnovijiPrvo).map(l -> odgovor(l, sada)));
	}

	// Traži se po paru link + organizacija iz putanje: admin druge organizacije s tuđim ID-jem dobije 404
	@Transactional
	public void revoke(UUID organizationId, UUID linkId, UUID adminId) {
		InviteLink link = links.findByIdAndOrganizationId(linkId, organizationId)
				.orElseThrow(() -> new InviteLinkNotUsableException(Reason.NOT_FOUND));
		if (link.getRevokedAt() == null) {
			link.povuci(clock.instant());
			auditLog.record(organizationId, adminId, AuditAction.INVITE_LINK_REVOKED, linkId, null);
		}
	}

	// Za ekran na vratima: adresa zadnjeg pozivnog linka koji još prima ljude, ako ga ima
	@Transactional(readOnly = true)
	public Optional<String> activeUrl(UUID organizationId) {
		Instant sada = clock.instant();
		return links.findFirstByOrganizationIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(organizationId,
				sada).filter(l -> l.vazi(sada)).map(this::url);
	}

	@Transactional(readOnly = true)
	public InviteLinkPreviewResponse preview(String token) {
		InviteLink link = links.findByTokenHash(LinkToken.hash(token))
				.orElseThrow(() -> new InviteLinkNotUsableException(Reason.NOT_FOUND));
		Organization organizacija = link.getOrganization();
		return new InviteLinkPreviewResponse(organizacija.getName(), organizacija.getSlug(), link.vazi(clock.instant()));
	}

	// Prvo članstvo, pa brojanje: ko je već član ne troši mjesto. Ako brojanje kaže da mjesta nema,
	// izuzetak vrati transakciju, pa nestane i upravo upisano članstvo.
	@Transactional
	public JoinedResponse join(UUID userId, String token) {
		InviteLink link = links.findByTokenHash(LinkToken.hash(token))
				.orElseThrow(() -> new InviteLinkNotUsableException(Reason.NOT_FOUND));
		Organization organizacija = link.getOrganization();
		Instant sada = clock.instant();
		if (link.getRevokedAt() != null) {
			throw new InviteLinkNotUsableException(Reason.REVOKED);
		}
		if (!sada.isBefore(link.getExpiresAt())) {
			throw new InviteLinkNotUsableException(Reason.EXPIRED);
		}
		User korisnik = users.findById(userId).orElseThrow();
		// Bez potvrđenog emaila bi link sa zida mogao napuniti organizaciju izmišljenim nalozima
		if (!korisnik.isEmailVerified()) {
			throw new InviteLinkNotUsableException(Reason.EMAIL_NOT_VERIFIED);
		}
		if (members.dodajKlijenta(organizacija.getId(), userId, sada) == 0) {
			return new JoinedResponse(organizacija.getId(), organizacija.getSlug(), organizacija.getName(), true);
		}
		if (links.iskoristi(link.getId(), sada) == 0) {
			throw new InviteLinkNotUsableException(Reason.FULL);
		}
		auditLog.record(organizacija.getId(), userId, AuditAction.INVITE_LINK_JOINED, link.getId(), null);
		return new JoinedResponse(organizacija.getId(), organizacija.getSlug(), organizacija.getName(), false);
	}

	private InviteLinkResponse odgovor(InviteLink link, Instant sada) {
		return new InviteLinkResponse(link.getId(), url(link), link.getMaxUses(), link.getUses(), link.getExpiresAt(),
				link.getRevokedAt(), link.getCreatedAt(), link.vazi(sada));
	}

	private String url(InviteLink link) {
		return appUrl + "/pridruzi/" + linkToken.za(LinkToken.Svrha.POZIVNI_LINK, link.getId());
	}

}
