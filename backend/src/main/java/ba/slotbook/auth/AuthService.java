package ba.slotbook.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import ba.slotbook.user.User;
import ba.slotbook.user.UserRepository;

@Service
public class AuthService {

	static final Duration ACCESS_TOKEN_TRAJE = Duration.ofMinutes(15);

	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final JwtEncoder jwtEncoder;
	private final Clock clock;
	private final String hashZaNepostojeciEmail;

	public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder, Clock clock) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.jwtEncoder = jwtEncoder;
		this.clock = clock;
		this.hashZaNepostojeciEmail = passwordEncoder.encode("lozinka-koja-se-nikad-ne-poklapa");
	}

	public TokenResponse login(String email, String password) {
		User user = users.findByEmail(User.normalizeEmail(email)).orElse(null);

		// BCrypt traje ~100 ms. Kad bismo za nepoznat email odmah vratili grešku, odgovor bi bio
		// primjetno brži, pa bi se mjerenjem vremena moglo saznati ko ima nalog.
		String hash = user != null ? user.getPasswordHash() : hashZaNepostojeciEmail;
		boolean lozinkaTacna = passwordEncoder.matches(password, hash);

		if (user == null || !lozinkaTacna) {
			throw new BadCredentialsException("Pogrešan email ili lozinka");
		}
		return new TokenResponse(accessToken(user), "Bearer", ACCESS_TOKEN_TRAJE.toSeconds());
	}

	private String accessToken(User user) {
		Instant sada = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer("slotbook")
				.subject(user.getId().toString())
				.issuedAt(sada)
				.expiresAt(sada.plus(ACCESS_TOKEN_TRAJE))
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

}
