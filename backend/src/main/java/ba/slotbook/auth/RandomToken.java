package ba.slotbook.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

// Nasumični token za refresh i pozivnice. Klijent dobije token, a baza čuva samo njegov hash.
public final class RandomToken {

	// SecureRandom je thread-safe, pa jedan dijele svi zahtjevi
	private static final SecureRandom RANDOM = new SecureRandom();

	private RandomToken() {
	}

	public static String generate() {
		byte[] bajtovi = new byte[32];
		RANDOM.nextBytes(bajtovi);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bajtovi);
	}

	// SHA-256, ne BCrypt: token je 32 nasumična bajta, pa ga sporost ne treba štititi od pogađanja,
	// a brz i uvijek isti hash nam treba da bismo token našli u bazi
	public static String sha256(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 mora postojati u svakoj Javi", e);
		}
	}

}
