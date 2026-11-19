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

@Service
public class PasswordResetService {

	// Kraće od potvrde emaila: ovaj link daje pristup nalogu
	static final Duration LINK_TRAJE = Duration.ofHours(1);

	private final UserRepository users;
	private final UserTokenService tokens;
	private final NotificationService notifications;
	private final PasswordEncoder passwordEncoder;
	private final RefreshTokenService refreshTokens;
	private final Clock clock;
	private final String appUrl;

	public PasswordResetService(UserRepository users, UserTokenService tokens, NotificationService notifications,
			PasswordEncoder passwordEncoder, RefreshTokenService refreshTokens, Clock clock,
			@Value("${slotbook.app-url}") String appUrl) {
		this.users = users;
		this.tokens = tokens;
		this.notifications = notifications;
		this.passwordEncoder = passwordEncoder;
		this.refreshTokens = refreshTokens;
		this.clock = clock;
		this.appUrl = appUrl;
	}

	// Za nepoznat email se ništa ne desi, a kontroler vraća isti odgovor, da se ne vidi ko ima nalog.
	// Preko granice mailova je isto: nema maila ni greške. Vlasniku to ne smeta, jer linkovi iz
	// prethodnih mailova i dalje važe.
	@Transactional
	public void request(String email) {
		users.findByEmail(User.normalizeEmail(email)).ifPresent(user -> {
			if (!notifications.waitBeforeNext(user.getId(), NotificationType.PASSWORD_RESET).isZero()) {
				return;
			}
			String link = appUrl + "/nova-lozinka#token=" + tokens.issue(user, UserTokenType.PASSWORD_RESET, LINK_TRAJE);
			notifications.enqueue(user.getId(), NotificationType.PASSWORD_RESET, new EmailContent(
					"Nova lozinka za SlotBook",
					"Zdravo " + user.getDisplayName() + ",\n\nza tvoj nalog na SlotBooku je zatražena nova lozinka. "
							+ "Ako si to ti, otvori link i upiši novu:\n" + link
							+ "\n\nLink važi sat vremena. Ako to nisi ti, zanemari ovaj mail i lozinka ostaje ista."));
		});
	}

	@Transactional
	public void reset(String token, String novaLozinka) {
		User user = tokens.use(token, UserTokenType.PASSWORD_RESET);
		user.changePassword(passwordEncoder.encode(novaLozinka));
		// Stariji linkovi za reset bi inače i dalje mogli promijeniti lozinku
		tokens.useAll(user, UserTokenType.PASSWORD_RESET);
		// Ko je znao staru lozinku, a možda je i zato mijenjamo, ostaje bez sesije
		refreshTokens.revokeAllOf(user.getId());
		// Link je stigao na ovaj email, pa je to dokaz kao i link za potvrdu
		user.verifyEmail(clock.instant());
	}

}
