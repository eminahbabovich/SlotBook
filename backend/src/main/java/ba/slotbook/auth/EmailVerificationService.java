package ba.slotbook.auth;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
	private final UserTokenService tokens;
	private final NotificationService notifications;
	private final Clock clock;
	private final String appUrl;

	public EmailVerificationService(UserService userService, UserRepository users, UserTokenService tokens,
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
		tokens.use(token, UserTokenType.EMAIL_VERIFY).verifyEmail(clock.instant());
	}

	// Token je u fragmentu (#), ne u ?query: preglednik fragment ne šalje serveru, pa ga nema u
	// logovima ni u Refereru. Stranica u frontendu ga pročita i pošalje na POST /api/auth/verify-email.
	private void posaljiLink(User user) {
		String link = appUrl + "/potvrda-emaila#token=" + tokens.issue(user, UserTokenType.EMAIL_VERIFY, LINK_TRAJE);
		notifications.enqueue(user.getId(), NotificationType.EMAIL_VERIFICATION, new EmailContent(
				"Potvrdi email za SlotBook",
				"Zdravo " + user.getDisplayName() + ",\n\nda potvrdiš da je ovo tvoj email, otvori link:\n" + link
						+ "\n\nLink važi 24 sata. Ako nalog na SlotBooku nije tvoj, zanemari ovaj mail."));
	}

}
