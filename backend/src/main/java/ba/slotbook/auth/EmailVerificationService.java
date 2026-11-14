package ba.slotbook.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.auth.VerificationTokenException.Reason;
import ba.slotbook.notification.EmailContent;
import ba.slotbook.notification.NotificationService;
import ba.slotbook.notification.NotificationType;
import ba.slotbook.user.User;
import ba.slotbook.user.UserRepository;
import ba.slotbook.user.UserResponse;
import ba.slotbook.user.UserService;

@Service
public class EmailVerificationService {

	static final Duration LINK_TRAJE = Duration.ofHours(24);

	private final UserService userService;
	private final UserRepository users;
	private final UserTokenRepository tokens;
	private final NotificationService notifications;
	private final Clock clock;
	private final String appUrl;

	public EmailVerificationService(UserService userService, UserRepository users, UserTokenRepository tokens,
			NotificationService notifications, Clock clock, @Value("${slotbook.app-url}") String appUrl) {
		this.userService = userService;
		this.users = users;
		this.tokens = tokens;
		this.notifications = notifications;
		this.clock = clock;
		this.appUrl = appUrl;
	}

	// Nalog i mail za potvrdu u istoj transakciji: nema naloga kojem link nikad nije ni krenuo
	@Transactional
	public UserResponse register(String email, String password, String displayName) {
		UserResponse novi = userService.register(email, password, displayName);
		posaljiLink(users.getReferenceById(novi.id()));
		return novi;
	}

	// Kad prvi mail ne stigne ili link istekne. Stari linkovi i dalje važe do svog roka.
	@Transactional
	public void resend(UUID userId) {
		User user = users.findById(userId).orElseThrow();
		if (!user.isEmailVerified()) {
			posaljiLink(user);
		}
	}

	@Transactional
	public void verify(String token) {
		UserToken zapis = tokens.findByTokenHashAndType(RandomToken.sha256(token), UserToken.Type.EMAIL_VERIFY)
				.orElseThrow(() -> new VerificationTokenException(Reason.NOT_FOUND));
		Instant sada = clock.instant();
		if (zapis.isExpired(sada)) {
			throw new VerificationTokenException(Reason.EXPIRED);
		}
		if (tokens.markUsed(zapis.getId(), sada) == 0) {
			throw new VerificationTokenException(Reason.ALREADY_USED);
		}
		zapis.getUser().verifyEmail(sada);
	}

	// Token je u fragmentu (#), ne u ?query: preglednik fragment ne šalje serveru, pa ga nema u
	// logovima ni u Refereru. Stranica u frontendu ga pročita i pošalje na POST /api/auth/verify-email.
	private void posaljiLink(User user) {
		String token = RandomToken.generate();
		Instant sada = clock.instant();
		tokens.save(new UserToken(user, UserToken.Type.EMAIL_VERIFY, RandomToken.sha256(token), sada,
				sada.plus(LINK_TRAJE)));
		String link = appUrl + "/potvrda-emaila#token=" + token;
		notifications.enqueue(user.getId(), NotificationType.EMAIL_VERIFICATION, new EmailContent(
				"Potvrdi email za SlotBook",
				"Zdravo " + user.getDisplayName() + ",\n\nda potvrdiš da je ovo tvoj email, otvori link:\n" + link
						+ "\n\nLink važi 24 sata. Ako nalog na SlotBooku nije tvoj, zanemari ovaj mail."));
	}

}
