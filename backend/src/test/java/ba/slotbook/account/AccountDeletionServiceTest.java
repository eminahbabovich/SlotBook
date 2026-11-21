package ba.slotbook.account;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import ba.slotbook.auth.LoginLockService;
import ba.slotbook.error.InvalidFieldException;
import ba.slotbook.ratelimit.TooManyAttemptsException;
import ba.slotbook.user.User;
import ba.slotbook.user.UserRepository;

class AccountDeletionServiceTest {

	private static final UUID ID = UUID.fromString("6f1c2b8e-3a4d-4c55-9e21-0b7d3f9a1c42");

	private final UserRepository users = mock(UserRepository.class);
	private final LoginLockService zakljucavanje = mock(LoginLockService.class);
	// Snaga 4: ovdje se testira redoslijed provjera, ne BCrypt
	private final PasswordEncoder passwordEncoder = spy(new BCryptPasswordEncoder(4));
	private final AccountAnonymization anonimizacija = mock(AccountAnonymization.class);
	private final AccountDeletionService service = new AccountDeletionService(users, zakljucavanje, passwordEncoder,
			anonimizacija);

	@BeforeEach
	void selmaImaNalog() {
		User selma = new User("selma.servis@example.com", passwordEncoder.encode("bjelasnica-84"), "Selma",
				Instant.parse("2026-11-01T10:00:00Z"));
		ReflectionTestUtils.setField(selma, "id", ID);
		when(users.findById(ID)).thenReturn(Optional.of(selma));
		when(zakljucavanje.lockedFor("selma.servis@example.com")).thenReturn(Duration.ZERO);
	}

	@Test
	void zakljucanNalogNeTrosiBcryptINistaNeBrise() {
		when(zakljucavanje.lockedFor("selma.servis@example.com")).thenReturn(Duration.ofMinutes(12));

		assertThatThrownBy(() -> service.delete(ID, "bjelasnica-84")).isInstanceOf(TooManyAttemptsException.class);

		verify(passwordEncoder, never()).matches(any(), anyString());
		verify(anonimizacija, never()).anonymize(any(), any());
	}

	@Test
	void pogresnaLozinkaSeBrojiINistaNeBrise() {
		assertThatThrownBy(() -> service.delete(ID, "pogresna")).isInstanceOf(InvalidFieldException.class);

		verify(zakljucavanje).recordFailure("selma.servis@example.com");
		verify(anonimizacija, never()).anonymize(any(), any());
	}

	@Test
	void tacnaLozinkaBriseNalog() {
		service.delete(ID, "bjelasnica-84");

		verify(anonimizacija).anonymize(ID, "selma.servis@example.com");
		verify(zakljucavanje, never()).recordFailure(anyString());
	}

}
