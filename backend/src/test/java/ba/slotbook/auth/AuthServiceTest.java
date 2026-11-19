package ba.slotbook.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import ba.slotbook.user.User;
import ba.slotbook.user.UserRepository;

class AuthServiceTest {

	private static final Instant SADA = Instant.parse("2026-11-12T10:00:00Z");
	private static final SecretKey KLJUC = new SecretKeySpec(
			"testni-kljuc-samo-za-testove-najmanje-32-bajta".getBytes(StandardCharsets.UTF_8), "HmacSHA256");

	private final UserRepository users = mock(UserRepository.class);
	// Snaga 4 umjesto 10, jer ovdje ne testiramo BCrypt nego tok prijave
	private final PasswordEncoder passwordEncoder = spy(new BCryptPasswordEncoder(4));
	private final RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
	// Zaključavanje ima svoje testove (LoginFailureTest, LoginLockApiTest), ovdje je uvijek otključano
	private final LoginLockService zakljucavanje = otkljucano();
	private final AuthService service = new AuthService(users, passwordEncoder,
			NimbusJwtEncoder.withSecretKey(KLJUC).algorithm(MacAlgorithm.HS256).build(),
			refreshTokens, zakljucavanje, Clock.fixed(SADA, ZoneOffset.UTC));

	private static LoginLockService otkljucano() {
		LoginLockService zakljucavanje = mock(LoginLockService.class);
		when(zakljucavanje.lockedFor(anyString())).thenReturn(Duration.ZERO);
		return zakljucavanje;
	}

	private User postojeciKorisnik(String email, String lozinka) {
		User user = new User(email, passwordEncoder.encode(lozinka), "Amra", SADA);
		// Nepotvrđen email se ne može prijaviti (EmailVerificationApiTest)
		user.verifyEmail(SADA);
		ReflectionTestUtils.setField(user, "id", UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e"));
		when(users.findByEmail(email)).thenReturn(Optional.of(user));
		return user;
	}

	// Rok tokena se ovdje ne provjerava jer je sat zamrznut u prošlosti; to radi LoginApiTest
	private Jwt procitaj(String token) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(KLJUC).macAlgorithm(MacAlgorithm.HS256).build();
		decoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
		return decoder.decode(token);
	}

	@Test
	void tacnaLozinkaDajeTokenSaIdemKorisnikaKojiTraje15Minuta() {
		User user = postojeciKorisnik("amra@example.com", "sarajevo2026");

		when(refreshTokens.issueNewFamily(user.getId())).thenReturn("refresh-iz-servisa");

		AuthService.Tokens tokens = service.login("amra@example.com", "sarajevo2026");
		TokenResponse odgovor = tokens.access();

		assertThat(tokens.refreshToken()).isEqualTo("refresh-iz-servisa");
		Jwt token = procitaj(odgovor.accessToken());
		assertThat(token.getSubject()).isEqualTo(user.getId().toString());
		assertThat(token.getIssuedAt()).isEqualTo(SADA);
		assertThat(token.getExpiresAt()).isEqualTo(SADA.plusSeconds(15 * 60));
		assertThat(odgovor.expiresIn()).isEqualTo(900);
	}

	@Test
	void emailSeTraziMalimSlovima() {
		postojeciKorisnik("amra@example.com", "sarajevo2026");

		assertThat(service.login("  AMRA@example.com", "sarajevo2026").access().accessToken()).isNotBlank();
	}

	@Test
	void pogresnaLozinkaSeOdbija() {
		postojeciKorisnik("amra@example.com", "sarajevo2026");

		assertThatThrownBy(() -> service.login("amra@example.com", "mostar2026"))
				.isInstanceOf(BadCredentialsException.class);
	}

	@Test
	void nepoznatEmailIpakProlaziKrozBcryptDaTrajeKaoPogresnaLozinka() {
		when(users.findByEmail("niko@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.login("niko@example.com", "sarajevo2026"))
				.isInstanceOf(BadCredentialsException.class);
		verify(passwordEncoder).matches(eq("sarajevo2026"), anyString());
	}

}
