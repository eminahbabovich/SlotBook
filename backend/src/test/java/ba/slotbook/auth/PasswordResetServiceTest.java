package ba.slotbook.auth;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import ba.slotbook.notification.NotificationService;
import ba.slotbook.notification.NotificationType;
import ba.slotbook.user.User;
import ba.slotbook.user.UserRepository;

class PasswordResetServiceTest {

	private final UserRepository users = mock(UserRepository.class);
	private final UserTokenService tokens = mock(UserTokenService.class);
	private final NotificationService notifications = mock(NotificationService.class);
	private final PasswordResetService service = new PasswordResetService(users, tokens, notifications,
			mock(PasswordEncoder.class), mock(RefreshTokenService.class), Clock.systemUTC(), "http://localhost:5173");

	// Email se traži normalizovan, kao kod prijave: " Amra@Example.com " je isti nalog
	@Test
	void emailSVelikimSlovimaIRazmacimaNalaziNalogIMailNosiLink() {
		UUID id = UUID.randomUUID();
		User amra = mock(User.class);
		when(amra.getId()).thenReturn(id);
		when(amra.getDisplayName()).thenReturn("Amra");
		when(users.findByEmail("amra@example.com")).thenReturn(Optional.of(amra));
		when(tokens.issue(amra, UserTokenType.PASSWORD_RESET, PasswordResetService.LINK_TRAJE)).thenReturn("abc123");

		service.request(" Amra@Example.com ");

		verify(notifications).enqueue(eq(id), eq(NotificationType.PASSWORD_RESET),
				argThat(mail -> mail.body().contains("http://localhost:5173/nova-lozinka#token=abc123")));
	}

}
