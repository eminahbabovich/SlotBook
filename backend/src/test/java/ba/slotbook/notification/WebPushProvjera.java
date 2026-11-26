package ba.slotbook.notification;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.jayway.jsonpath.JsonPath;

// Strana browsera i push servisa, samo za testove: dešifrovanje po RFC 8291 (aes128gcm) i provjera
// VAPID potpisa po RFC 8292. Pisano iz RFC-a, nezavisno od biblioteke koja šifruje, da test ne bi
// samo potvrdio da biblioteka slaže sama sa sobom.
final class WebPushProvjera {

	private WebPushProvjera() {
	}

	// Tijelo: salt (16) | veličina zapisa (4) | dužina ključa (1) | javni ključ servera | šifrovano
	static byte[] desifruj(byte[] tijelo, KeyPair browser, byte[] auth) throws Exception {
		ByteBuffer citac = ByteBuffer.wrap(tijelo);
		byte[] salt = new byte[16];
		citac.get(salt);
		citac.getInt();
		byte[] kljucServera = new byte[citac.get() & 0xff];
		citac.get(kljucServera);
		byte[] sifrovano = new byte[citac.remaining()];
		citac.get(sifrovano);

		byte[] javniBrowsera = nekomprimovan((ECPublicKey) browser.getPublic());
		KeyAgreement ecdh = KeyAgreement.getInstance("ECDH");
		ecdh.init(browser.getPrivate());
		ecdh.doPhase(javniKljuc(kljucServera), true);
		byte[] zajednicka = ecdh.generateSecret();

		byte[] prkKljuc = hmac(auth, zajednicka);
		byte[] ikm = hmac(prkKljuc, spoji("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), javniBrowsera,
				kljucServera, new byte[] { 1 }));
		byte[] prk = hmac(salt, ikm);
		byte[] cek = Arrays.copyOf(hmac(prk, spoji("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII),
				new byte[] { 1 })), 16);
		byte[] nonce = Arrays.copyOf(hmac(prk, spoji("Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII),
				new byte[] { 1 })), 12);

		Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
		aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
		byte[] zapis = aes.doFinal(sifrovano);
		// Zadnji zapis završava graničnikom 0x02, a iza njega mogu biti nule (dopuna)
		int kraj = zapis.length - 1;
		while (zapis[kraj] == 0) {
			kraj--;
		}
		if (zapis[kraj] != 2) {
			throw new IllegalStateException("Nema graničnika zadnjeg zapisa");
		}
		return Arrays.copyOf(zapis, kraj);
	}

	// "vapid t=<JWT>, k=<javni ključ>": potpis ES256 (r||s) mora biti ispravan za javni ključ servera
	static Map<String, Object> provjeriVapid(String zaglavlje, String javniKljucServera) throws Exception {
		String jwt = zaglavlje.replaceFirst("^vapid t=([^,]+),.*$", "$1");
		String[] dijelovi = jwt.split("\\.");
		byte[] rs = Base64.getUrlDecoder().decode(dijelovi[2]);
		Signature es256 = Signature.getInstance("SHA256withECDSAinP1363Format");
		es256.initVerify(javniKljuc(Base64.getUrlDecoder().decode(javniKljucServera)));
		es256.update((dijelovi[0] + "." + dijelovi[1]).getBytes(StandardCharsets.US_ASCII));
		if (rs.length != 64 || !es256.verify(rs)) {
			throw new IllegalStateException("VAPID potpis nije ispravan");
		}
		return JsonPath.read(new String(Base64.getUrlDecoder().decode(dijelovi[1]), StandardCharsets.UTF_8), "$");
	}

	private static PublicKey javniKljuc(byte[] nekomprimovan) throws Exception {
		AlgorithmParameters parametri = AlgorithmParameters.getInstance("EC");
		parametri.init(new ECGenParameterSpec("secp256r1"));
		ECPoint tacka = new ECPoint(new BigInteger(1, Arrays.copyOfRange(nekomprimovan, 1, 33)),
				new BigInteger(1, Arrays.copyOfRange(nekomprimovan, 33, 65)));
		return KeyFactory.getInstance("EC")
				.generatePublic(new ECPublicKeySpec(tacka, parametri.getParameterSpec(ECParameterSpec.class)));
	}

	private static byte[] nekomprimovan(ECPublicKey kljuc) {
		byte[] rezultat = new byte[65];
		rezultat[0] = 4;
		kopiraj32(kljuc.getW().getAffineX(), rezultat, 1);
		kopiraj32(kljuc.getW().getAffineY(), rezultat, 33);
		return rezultat;
	}

	private static void kopiraj32(BigInteger broj, byte[] u, int od) {
		byte[] b = broj.toByteArray();
		int duzina = Math.min(b.length, 32);
		System.arraycopy(b, b.length - duzina, u, od + 32 - duzina, duzina);
	}

	private static byte[] hmac(byte[] kljuc, byte[] podaci) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(kljuc, "HmacSHA256"));
		return mac.doFinal(podaci);
	}

	private static byte[] spoji(byte[]... dijelovi) {
		ByteBuffer b = ByteBuffer.allocate(Arrays.stream(dijelovi).mapToInt(d -> d.length).sum());
		for (byte[] d : dijelovi) {
			b.put(d);
		}
		return b.array();
	}

}
