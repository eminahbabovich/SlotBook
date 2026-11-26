package ba.slotbook.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Token za link koji se dijeli (pozivni link na plakatu, ekran na vratima): HMAC-SHA256 nad ID-jem
// reda, kao QR za dolazak (CheckInToken). Baza čuva samo SHA-256 tokena, pa ko pročita bazu ne može
// otvoriti link. Server ga ipak može ponovo izračunati iz ID-ja, pa admin vidi link i sedmicu
// kasnije, za novi plakat. Vidi docs/ODLUKE.md.
@Component
public class LinkToken {

	public enum Svrha {
		POZIVNI_LINK, EKRAN
	}

	private static final String ALGORITAM = "HmacSHA256";

	private final SecretKeySpec kljuc;

	// Ključ iz JWT tajne s oznakom svrhe, kao kod QR-a za dolazak: nema nove varijable okruženja, a
	// JWT i linkovi nikad nisu potpisani istim ključem. Nova JWT tajna poništi i sve linkove.
	LinkToken(@Value("${slotbook.jwt.secret}") String tajna) {
		this.kljuc = new SecretKeySpec(
				hmac(new SecretKeySpec(tajna.getBytes(StandardCharsets.UTF_8), ALGORITAM), "slotbook linkovi v1"),
				ALGORITAM);
	}

	// Svrha je u poruci, pa token ekrana ne otvara pozivni link s istim ID-jem i obrnuto
	public String za(Svrha svrha, UUID id) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(kljuc, svrha + ":" + id));
	}

	public static String hash(String token) {
		return RandomToken.sha256(token);
	}

	private static byte[] hmac(SecretKeySpec kljuc, String poruka) {
		try {
			Mac mac = Mac.getInstance(ALGORITAM);
			mac.init(kljuc);
			return mac.doFinal(poruka.getBytes(StandardCharsets.UTF_8));
		} catch (GeneralSecurityException e) {
			// HmacSHA256 mora postojati u svakoj Javi, pa ovo znači pokvarenu instalaciju
			throw new IllegalStateException(e);
		}
	}

}
