package ba.slotbook.screen;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.audit.AuditAction;
import ba.slotbook.audit.AuditLogService;
import ba.slotbook.auth.LinkToken;
import ba.slotbook.booking.FreeSlotService;
import ba.slotbook.booking.SlotCalendarResponse;
import ba.slotbook.organization.InviteLinkService;
import ba.slotbook.organization.Organization;
import ba.slotbook.resource.Resource;
import ba.slotbook.resource.ResourceNotFoundException;
import ba.slotbook.resource.ResourceRepository;
import ba.slotbook.screen.ScreenView.ScreenResource;
import ba.slotbook.screen.ScreenView.ScreenSlot;
import ba.slotbook.user.UserRepository;

@Service
public class DoorScreenService {

	private final DoorScreenRepository screens;
	private final ResourceRepository resources;
	private final UserRepository users;
	private final FreeSlotService freeSlots;
	private final InviteLinkService inviteLinks;
	private final AuditLogService auditLog;
	private final LinkToken linkToken;
	private final ApplicationEventPublisher objave;
	private final Clock clock;
	private final String appUrl;

	public DoorScreenService(DoorScreenRepository screens, ResourceRepository resources, UserRepository users,
			FreeSlotService freeSlots, InviteLinkService inviteLinks, AuditLogService auditLog, LinkToken linkToken,
			ApplicationEventPublisher objave, Clock clock, @Value("${slotbook.app-url}") String appUrl) {
		this.screens = screens;
		this.resources = resources;
		this.users = users;
		this.freeSlots = freeSlots;
		this.inviteLinks = inviteLinks;
		this.auditLog = auditLog;
		this.linkToken = linkToken;
		this.objave = objave;
		this.clock = clock;
		this.appUrl = appUrl;
	}

	// Novi ekran za isto mjesto gasi stari: link koji je ostao na starom tabletu ili u nečijem chatu
	// prestaje raditi, a admin ne mora pamtiti da ga ugasi posebno
	@Transactional
	public DoorScreenResponse create(UUID organizationId, UUID resourceId, UUID adminId) {
		Organization organizacija = screens.lockOrganization(organizationId).orElseThrow();
		Resource resurs = resourceId == null ? null
				: resources.findByIdAndOrganizationId(resourceId, organizationId)
						.orElseThrow(ResourceNotFoundException::new);
		Instant sada = clock.instant();
		Optional<DoorScreen> stari = resourceId == null
				? screens.findByOrganizationIdAndResourceIsNullAndRevokedAtIsNull(organizationId)
				: screens.findByOrganizationIdAndResourceIdAndRevokedAtIsNull(organizationId, resourceId);
		stari.ifPresent(s -> ugasi(s, adminId, sada));
		// Hibernate pri flushu upisuje nove redove prije izmjena, pa bi novi ekran došao prije gašenja
		// starog i pao na door_screens_jedan_zivi
		screens.flush();
		UUID id = UUID.randomUUID();
		DoorScreen ekran = screens.save(new DoorScreen(id, organizacija, resurs,
				LinkToken.hash(linkToken.za(LinkToken.Svrha.EKRAN, id)), users.getReferenceById(adminId), sada));
		auditLog.record(organizationId, adminId, AuditAction.DOOR_SCREEN_CREATED, id,
				resurs == null ? "cijela organizacija" : resurs.getName());
		return odgovor(ekran);
	}

	@Transactional(readOnly = true)
	public List<DoorScreenResponse> list(UUID organizationId) {
		return screens.findLive(organizationId).stream().map(this::odgovor).toList();
	}

	// Po paru ekran + organizacija iz putanje: admin druge organizacije s tuđim ID-jem dobije 404
	@Transactional
	public void revoke(UUID organizationId, UUID screenId, UUID adminId) {
		DoorScreen ekran = screens.findByIdAndOrganizationId(screenId, organizationId)
				.orElseThrow(DoorScreenNotFoundException::new);
		if (ekran.getRevokedAt() == null) {
			ugasi(ekran, adminId, clock.instant());
		}
	}

	// Današnji dan u zoni organizacije, za sve aktivne resurse (ili jedan). Ugašen ekran je 404.
	@Transactional(readOnly = true)
	public ScreenView view(String token) {
		DoorScreen ekran = zivi(token);
		Organization organizacija = ekran.getOrganization();
		Instant sada = clock.instant();
		LocalDate danas = LocalDate.ofInstant(sada, ZoneId.of(organizacija.getTimezone()));
		List<ScreenResource> resursi = new ArrayList<>();
		for (Resource resurs : resursiEkrana(ekran)) {
			SlotCalendarResponse dan = freeSlots.wholeDay(resurs.getId(), danas);
			List<ScreenSlot> termini = new ArrayList<>();
			dan.slots().forEach(t -> termini.add(new ScreenSlot(t.start(), t.end(), t.seatsLeft())));
			dan.taken().forEach(t -> termini.add(new ScreenSlot(t.start(), t.end(), 0)));
			termini.sort(Comparator.comparing(ScreenSlot::start));
			resursi.add(new ScreenResource(resurs.getId(), resurs.getName(), resurs.getCapacity(), termini));
		}
		return new ScreenView(organizacija.getName(), organizacija.getTimezone(), danas, sada,
				inviteLinks.activeUrl(organizacija.getId()).orElse(null), resursi);
	}

	// Za SSE: ID ekrana i resursi čije promjene treba javiti
	@Transactional(readOnly = true)
	public ScreenStream.Pretplata subscription(String token) {
		DoorScreen ekran = zivi(token);
		return new ScreenStream.Pretplata(ekran.getId(),
				resursiEkrana(ekran).stream().map(Resource::getId).toList());
	}

	private DoorScreen zivi(String token) {
		return screens.findByTokenHash(LinkToken.hash(token))
				.filter(s -> s.getRevokedAt() == null)
				.orElseThrow(DoorScreenNotFoundException::new);
	}

	private List<Resource> resursiEkrana(DoorScreen ekran) {
		if (ekran.getResource() != null) {
			return ekran.getResource().isActive() ? List.of(ekran.getResource()) : List.of();
		}
		return resources.findByOrganizationIdOrderByNameAscIdAsc(ekran.getOrganization().getId()).stream()
				.filter(Resource::isActive)
				.toList();
	}

	private void ugasi(DoorScreen ekran, UUID adminId, Instant sada) {
		ekran.ugasi(sada);
		auditLog.record(ekran.getOrganization().getId(), adminId, AuditAction.DOOR_SCREEN_REVOKED, ekran.getId(),
				null);
		objave.publishEvent(new DoorScreenRevoked(ekran.getId()));
	}

	private DoorScreenResponse odgovor(DoorScreen ekran) {
		Resource resurs = ekran.getResource();
		return new DoorScreenResponse(ekran.getId(), resurs == null ? null : resurs.getId(),
				resurs == null ? null : resurs.getName(),
				appUrl + "/ekran/" + linkToken.za(LinkToken.Svrha.EKRAN, ekran.getId()), ekran.getCreatedAt());
	}

}
