package ba.slotbook.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

	static final Duration TRAJE = Duration.ofDays(30);

	private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

	private final RefreshTokenRepository tokens;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	public RefreshTokenService(RefreshTokenRepository tokens, Clock clock) {
		this.tokens = tokens;
		this.clock = clock;
	}

	public record Rotation(UUID userId, String refreshToken) {
	}

	private record Issued(UUID id, String refreshToken) {
	}

	@Transactional
	public String issueNewFamily(UUID userId) {
		return issue(userId, UUID.randomUUID()).refreshToken();
	}

	// noRollbackFor: kod ponovne upotrebe poništimo porodicu pa bacimo izuzetak. Bez ovoga bi
	// izuzetak vratio transakciju unazad i porodica bi ostala važeća, tj. lopov bi i dalje imao pristup.
	@Transactional(noRollbackFor = InvalidRefreshTokenException.class)
	public Rotation rotate(String refreshToken) {
		if (refreshToken == null) {
			throw new InvalidRefreshTokenException();
		}
		Instant sada = clock.instant();
		RefreshToken stari = tokens.findByTokenHash(hash(refreshToken))
				.orElseThrow(InvalidRefreshTokenException::new);

		if (stari.isRevoked()) {
			// Ovaj token je već zamijenjen novim, a neko ga šalje ponovo: jedan od dvojice ga je ukrao
			int ponisteno = tokens.revokeFamily(stari.getFamilyId(), sada);
			log.warn("Ponovo upotrijebljen refresh token, porodica {} poništena ({} aktivnih tokena)",
					stari.getFamilyId(), ponisteno);
			throw new InvalidRefreshTokenException();
		}
		if (stari.isExpired(sada)) {
			throw new InvalidRefreshTokenException();
		}

		Issued novi = issue(stari.getUserId(), stari.getFamilyId());
		stari.replaceWith(novi.id(), sada);
		return new Rotation(stari.getUserId(), novi.refreshToken());
	}

	@Transactional
	public void revokeFamily(String refreshToken) {
		if (refreshToken == null) {
			return;
		}
		tokens.findByTokenHash(hash(refreshToken))
				.ifPresent(token -> tokens.revokeFamily(token.getFamilyId(), clock.instant()));
	}

	private Issued issue(UUID userId, UUID familyId) {
		byte[] bajtovi = new byte[32];
		random.nextBytes(bajtovi);
		String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bajtovi);

		Instant sada = clock.instant();
		RefreshToken token = tokens.save(
				new RefreshToken(userId, familyId, hash(refreshToken), sada, sada.plus(TRAJE)));
		return new Issued(token.getId(), refreshToken);
	}

	// SHA-256, ne BCrypt: token je 32 nasumična bajta, pa ga sporost ne treba štititi od pogađanja,
	// a brz i uvijek isti hash nam treba da bismo token našli u bazi
	static String hash(String refreshToken) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(refreshToken.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 mora postojati u svakoj Javi", e);
		}
	}

}
