package ba.slotbook.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import ba.slotbook.ratelimit.TooManyAttemptsException;
import ba.slotbook.user.User;
import ba.slotbook.user.UserRepository;

@Service
public class AuthService {

	static final Duration ACCESS_TOKEN_TRAJE = Duration.ofMinutes(15);

	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final JwtEncoder jwtEncoder;
	private final RefreshTokenService refreshTokens;
	private final LoginLockService zakljucavanje;
	private final Clock clock;
	private final String hashZaNepostojeciEmail;

	public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
			RefreshTokenService refreshTokens, LoginLockService zakljucavanje, Clock clock) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.jwtEncoder = jwtEncoder;
		this.refreshTokens = refreshTokens;
		this.zakljucavanje = zakljucavanje;
		this.clock = clock;
		this.hashZaNepostojeciEmail = passwordEncoder.encode("lozinka-koja-se-nikad-ne-poklapa");
	}

	// Access token ide u tijelo odgovora, a refresh token kontroler stavlja u kolačić
	public record Tokens(TokenResponse access, String refreshToken) {
	}

	public Tokens login(String email, String password) {
		// Prije BCrypt-a: dok je zaključano, ni tačna lozinka ne pomaže, pa je ne treba ni provjeravati
		Duration zakljucano = zakljucavanje.lockedFor(email);
		if (!zakljucano.isZero()) {
			throw new TooManyAttemptsException(TooManyAttemptsException.Razlog.NALOG, zakljucano);
		}
		User user = users.findByEmail(User.normalizeEmail(email)).orElse(null);

		// BCrypt traje ~100 ms. Kad bismo za nepoznat email odmah vratili grešku, odgovor bi bio
		// primjetno brži, pa bi se mjerenjem vremena moglo saznati ko ima nalog.
		String hash = user != null ? user.getPasswordHash() : hashZaNepostojeciEmail;
		boolean lozinkaTacna = passwordEncoder.matches(password, hash);

		// Nepotvrđen email dobije isti odgovor kao pogrešna lozinka i broji se isto. Inače bi registracija
		// tuđim emailom pa prijava svojom lozinkom otkrila da li je nalog već postojao.
		if (user == null || !lozinkaTacna || !user.isEmailVerified()) {
			zakljucavanje.recordFailure(email);
			throw new BadCredentialsException("Pogrešan email ili lozinka");
		}
		zakljucavanje.clear(email);
		return new Tokens(accessToken(user.getId()), refreshTokens.issueNewFamily(user.getId()));
	}

	public Tokens refresh(String refreshToken) {
		RefreshTokenService.Rotation rotacija = refreshTokens.rotate(refreshToken);
		return new Tokens(accessToken(rotacija.userId()), rotacija.refreshToken());
	}

	public void logout(String refreshToken) {
		refreshTokens.revokeFamily(refreshToken);
	}

	private TokenResponse accessToken(UUID userId) {
		Instant sada = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer("slotbook")
				.subject(userId.toString())
				.issuedAt(sada)
				.expiresAt(sada.plus(ACCESS_TOKEN_TRAJE))
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new TokenResponse(token, "Bearer", ACCESS_TOKEN_TRAJE.toSeconds());
	}

}
