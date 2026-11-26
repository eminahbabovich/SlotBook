package ba.slotbook.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

import ba.slotbook.user.UserRepository;

@Service
public class NotificationService {

	// Najviše toliko mailova iste vrste istom primaocu u satu, da se nečiji inbox ne može zatrpati
	static final int MAILOVA_NA_SAT = 3;
	private static final Duration SAT = Duration.ofHours(1);

	// Na telefon ide samo ono zbog čega treba nešto uraditi ili ne zakasniti. Ostalo stiže samo mailom.
	// Podsjetnik nosi tekst maila (dan i sat termina): posao koji kasni ga pošalje i par sati prije termina,
	// pa fiksno "sutra" ne bi bilo tačno.
	private static final Map<NotificationType, String> PUSH = Map.of(
			NotificationType.WAITLIST_OFFER, "Termin s liste čekanja je tvoj ako ga potvrdiš prije nego što ponuda istekne.",
			NotificationType.BOOKING_CONFIRMED, "Termin je potvrđen. Detalji su u Mojim rezervacijama.",
			NotificationType.BOOKING_REJECTED, "Rezervacija nije prihvaćena. Možeš izabrati drugi termin.");
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final NotificationRepository notifications;
	private final UserRepository users;
	private final PushSubscriptionRepository pretplate;
	private final WebPushClient webPush;
	private final Clock clock;

	public NotificationService(NotificationRepository notifications, UserRepository users,
			PushSubscriptionRepository pretplate, WebPushClient webPush, Clock clock) {
		this.notifications = notifications;
		this.users = users;
		this.pretplate = pretplate;
		this.webPush = webPush;
		this.clock = clock;
	}

	// MANDATORY: bez transakcije pozivaoca Spring baci izuzetak. Obavijest upisana u svojoj
	// transakciji ostala bi i kad rezervacija padne, a outbox postoji baš da se to ne može desiti.
	@Transactional(propagation = Propagation.MANDATORY)
	public void enqueue(UUID userId, NotificationType type, EmailContent content) {
		notifications.save(new Notification(users.getReferenceById(userId), type, null, content, clock.instant()));
		naUredjaje(userId, type, null, content);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void enqueueForBooking(UUID userId, NotificationType type, UUID bookingId, EmailContent content) {
		notifications.save(
				new Notification(users.getReferenceById(userId), type, bookingId, content, clock.instant()));
		naUredjaje(userId, type, bookingId, content);
	}

	// Uz mail još po jedna obavijest za svaki browser korisnika, u istoj transakciji i istom redu čekanja
	private void naUredjaje(UUID userId, NotificationType type, UUID bookingId, EmailContent content) {
		String recenica = type == NotificationType.BOOKING_REMINDER ? content.body() : PUSH.get(type);
		if (recenica == null || !webPush.ukljucen()) {
			return;
		}
		String poruka = JSON.writeValueAsString(new PushMessage(content.subject(), recenica, "/rezervacije"));
		for (PushSubscription pretplata : pretplate.findByUserId(userId)) {
			notifications.save(Notification.push(users.getReferenceById(userId), type, bookingId, pretplata,
					content.subject(), poruka, clock.instant()));
		}
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void enqueueInvite(String email, UUID inviteId, EmailContent content) {
		notifications.save(new Notification(email, inviteId, content, clock.instant()));
	}

	// Mail stare pozivnice koji još nije otišao nema smisla slati, jer bi link vratio 404. Otišli ostaje,
	// da ga granica broji.
	@Transactional(propagation = Propagation.MANDATORY)
	public void dropPendingInvite(UUID organizationId, String email) {
		notifications.deletePendingForOpenInvite(organizationId, email);
	}

	// Koliko treba čekati na sljedeći mail ove vrste za korisnika: nula dok je ispod granice
	public Duration waitBeforeNext(UUID userId, NotificationType type) {
		Instant sada = clock.instant();
		return cekanje(notifications.createdForUserSince(userId, type, sada.minus(SAT),
				PageRequest.of(0, MAILOVA_NA_SAT)), sada);
	}

	// Pozivnica ide na email koji možda još nema nalog, pa se broji po emailu, preko svih organizacija
	public Duration waitBeforeNextInvite(String email) {
		Instant sada = clock.instant();
		return cekanje(notifications.createdForEmailSince(email, NotificationType.ORGANIZATION_INVITE,
				sada.minus(SAT), PageRequest.of(0, MAILOVA_NA_SAT)), sada);
	}

	// Mjesto se oslobodi kad najstariji od zadnjih MAILOVA_NA_SAT izađe iz sata
	private static Duration cekanje(List<Instant> zadnji, Instant sada) {
		if (zadnji.size() < MAILOVA_NA_SAT) {
			return Duration.ZERO;
		}
		return Duration.between(sada, zadnji.getLast().plus(SAT));
	}

}
