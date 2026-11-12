package ba.slotbook.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceTest {

	private static final Instant SADA = Instant.parse("2026-11-12T10:00:00Z");

	private final UserRepository users = mock(UserRepository.class);
	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
	private final UserService service =
			new UserService(users, passwordEncoder, Clock.fixed(SADA, ZoneOffset.UTC));

	@BeforeEach
	void repozitorijVracaOnoStoJeSpremio() {
		when(users.saveAndFlush(any())).thenAnswer(poziv -> poziv.getArgument(0));
	}

	@Test
	void uBazuIdeHashANeLozinka() {
		service.register("amra@example.com", "sarajevo2026", "Amra Hodžić");

		ArgumentCaptor<User> spremljen = ArgumentCaptor.forClass(User.class);
		Mockito.verify(users).saveAndFlush(spremljen.capture());

		assertThat(spremljen.getValue().getPasswordHash()).isNotEqualTo("sarajevo2026");
		assertThat(passwordEncoder.matches("sarajevo2026", spremljen.getValue().getPasswordHash())).isTrue();
	}

	@Test
	void vrijemeRegistracijeDolaziIzSata() {
		service.register("amra@example.com", "sarajevo2026", "Amra Hodžić");

		ArgumentCaptor<User> spremljen = ArgumentCaptor.forClass(User.class);
		Mockito.verify(users).saveAndFlush(spremljen.capture());

		assertThat(spremljen.getValue().getCreatedAt()).isEqualTo(SADA);
	}

	@Test
	void greskaJedinstvenostiIzBazePostajeZauzetEmail() {
		when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("users_email_key"));

		assertThatThrownBy(() -> service.register("amra@example.com", "sarajevo2026", "Amra"))
				.isInstanceOf(EmailAlreadyUsedException.class);
	}

}
