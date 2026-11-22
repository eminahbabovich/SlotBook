package ba.slotbook.booking;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Token u QR kodu: HMAC-SHA256 nad ID-jem rezervacije. Ne čuva se u bazi, nego se izračuna ponovo
// i uporedi. Ko nema ključ, ne može napraviti token za tuđu rezervaciju ni pogoditi ga.
@Component
class CheckInToken {

	private static final String ALGORITAM = "HmacSHA256";

	private final SecretKeySpec kljuc;

	// Ključ se izvodi iz JWT tajne s oznakom svrhe, pa nema nove varijable okruženja, a potpis
	// QR tokena i potpis JWT-a nikad nisu pod istim ključem.
	CheckInToken(@Value("${slotbook.jwt.secret}") String tajna) {
		this.kljuc = new SecretKeySpec(hmac(new SecretKeySpec(tajna.getBytes(StandardCharsets.UTF_8), ALGORITAM),
				"slotbook check-in v1"), ALGORITAM);
	}

	String za(UUID rezervacija) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(kljuc, rezervacija.toString()));
	}

	// MessageDigest.isEqual poredi u konstantnom vremenu, pa trajanje poređenja ne otkriva koliko
	// je znakova pogođeno
	boolean vazi(UUID rezervacija, String token) {
		return token != null && MessageDigest.isEqual(za(rezervacija).getBytes(StandardCharsets.US_ASCII),
				token.getBytes(StandardCharsets.US_ASCII));
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
