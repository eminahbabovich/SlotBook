package ba.slotbook.auth;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
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
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;
	private final String appUrl;

	public EmailVerificationService(UserService userService, UserRepository users, UserTokenService tokens,
			NotificationService notifications, PasswordEncoder passwordEncoder, Clock clock,
			@Value("${slotbook.app-url}") String appUrl) {
		this.userService = userService;
		this.users = users;
		this.tokens = tokens;
		this.notifications = notifications;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
		this.appUrl = appUrl;
	}

	// Kontroler uvijek vraća isti odgovor, pa se po njemu ne vidi da li je nalog već postojao. Šta se
	// desilo, saznaje samo vlasnik emaila iz maila. Nalog i mail za potvrdu su u istoj transakciji:
	// nema naloga kojem link nikad nije ni krenuo.
	@Transactional
	public void register(String email, String password, String displayName) {
		User postojeci = users.findByEmail(User.normalizeEmail(email)).orElse(null);
		if (postojeci == null) {
			UserResponse novi = userService.register(email, password, displayName);
			posaljiLink(users.getReferenceById(novi.id()));
			return;
		}
		// Novi nalog troši BCrypt, pa ga troši i ova grana. Bez toga bi odgovor za postojeći email bio
		// brži za trajanje BCrypt-a i mjerenjem bi se saznalo ko ima nalog (VrijemeOdgovoraTest).
		passwordEncoder.encode(password);
		if (!postojeci.isEmailVerified()) {
			resend(email);
		} else if (notifications.waitBeforeNext(postojeci.getId(), NotificationType.ACCOUNT_EXISTS).isZero()) {
			notifications.enqueue(postojeci.getId(), NotificationType.ACCOUNT_EXISTS, new EmailContent(
					"Neko je pokušao napraviti nalog s tvojim emailom",
					"Zdravo " + postojeci.getDisplayName() + ",\n\nneko je upravo pokušao napraviti nalog na "
							+ "SlotBooku s ovim emailom. Nalog već imaš, pa novi nije napravljen i tvoj se nije "
							+ "promijenio.\n\nAko si to ti, prijavi se: " + appUrl + "/prijava\nAko se ne "
							+ "sjećaš lozinke, novu možeš postaviti sa stranice za prijavu. Ako to nisi ti, "
							+ "zanemari ovaj mail."));
		}
	}

	// Kad prvi mail ne stigne ili link istekne. Stari linkovi i dalje važe do svog roka. Bez prijave,
	// jer se nepotvrđen nalog ne može prijaviti, pa kontroler uvijek vraća 204: za nepoznat email,
	// potvrđen email i preko granice mailova se samo ništa ne pošalje.
	@Transactional
	public void resend(String email) {
		users.findByEmail(User.normalizeEmail(email))
				.filter(user -> !user.isEmailVerified())
				.filter(user -> notifications.waitBeforeNext(user.getId(), NotificationType.EMAIL_VERIFICATION).isZero())
				.ifPresent(this::posaljiLink);
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
