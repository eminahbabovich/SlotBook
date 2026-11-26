package ba.slotbook.notification;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.zerodeplibs.webpush.VAPIDKeyPair;
import com.zerodeplibs.webpush.VAPIDKeyPairs;
import com.zerodeplibs.webpush.httpclient.StandardHttpClientRequestPreparer;
import com.zerodeplibs.webpush.key.PrivateKeySources;
import com.zerodeplibs.webpush.key.PublicKeySources;

// Šifrovanje poruke (RFC 8291) i VAPID potpis radi zerodep-web-push-java, a šalje JDK HttpClient.
// Bez oba ključa push je isključen: pretplate se ne primaju, a obavijesti idu samo mailom.
@Component
class WebPushClient {

	// Šalje se samo na adrese poznatih push servisa. Endpoint dolazi iz browsera, odnosno od korisnika,
	// pa bi bez ove liste server slao POST na bilo koju adresu koju mu neko podmetne (SSRF).
	private static final List<String> PUSH_SERVISI = List.of("fcm.googleapis.com", "push.services.mozilla.com",
			"push.apple.com", "notify.windows.com");

	private final VAPIDKeyPair kljucevi;
	private final String javniKljuc;
	private final String subject;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	WebPushClient(@Value("${slotbook.push.vapid-public-key:}") String javniKljuc,
			@Value("${slotbook.push.vapid-private-key:}") String privatniKljuc,
			@Value("${slotbook.push.subject}") String subject) {
		this.subject = subject;
		if (javniKljuc.isBlank() || privatniKljuc.isBlank()) {
			this.kljucevi = null;
			this.javniKljuc = null;
			return;
		}
		this.javniKljuc = javniKljuc.strip();
		this.kljucevi = VAPIDKeyPairs.of(PrivateKeySources.ofECPrivateKey(privatni(privatniKljuc.strip())),
				PublicKeySources.ofUncompressedBytes(Base64.getUrlDecoder().decode(this.javniKljuc)));
	}

	boolean ukljucen() {
		return kljucevi != null;
	}

	// Base64url kao što ga daje "npx web-push generate-vapid-keys". Browser ga treba za pushManager.subscribe.
	String javniKljuc() {
		return javniKljuc;
	}

	static boolean poznatServis(String endpoint) {
		URI adresa;
		try {
			adresa = URI.create(endpoint);
		} catch (IllegalArgumentException e) {
			return false;
		}
		String host = adresa.getHost() == null ? "" : adresa.getHost().toLowerCase(Locale.ROOT);
		return "https".equals(adresa.getScheme())
				&& PUSH_SERVISI.stream().anyMatch(s -> host.equals(s) || host.endsWith("." + s));
	}

	// Vraća HTTP status push servisa: 201 je primljeno, 404 i 410 znače da pretplata više ne postoji.
	// TTL od dva dana: ugašen telefon dobije obavijest kad se upali, ako je još aktuelna.
	int posalji(PushSubscription pretplata, String poruka) throws IOException, InterruptedException {
		com.zerodeplibs.webpush.PushSubscription prima = new com.zerodeplibs.webpush.PushSubscription();
		prima.setEndpoint(pretplata.getEndpoint());
		com.zerodeplibs.webpush.PushSubscription.Keys kljuceviPrimaoca = new com.zerodeplibs.webpush.PushSubscription.Keys();
		kljuceviPrimaoca.setP256dh(pretplata.getP256dh());
		kljuceviPrimaoca.setAuth(pretplata.getAuth());
		prima.setKeys(kljuceviPrimaoca);
		HttpRequest zahtjev = StandardHttpClientRequestPreparer.getBuilder()
				.pushSubscription(prima)
				.vapidJWTExpiresAfter(12, TimeUnit.HOURS)
				.vapidJWTSubject(subject)
				.pushMessage(poruka)
				.ttl(2, TimeUnit.DAYS)
				.urgencyNormal()
				.build(kljucevi)
				.toRequestBuilder()
				.timeout(Duration.ofSeconds(10))
				.build();
		return http.send(zahtjev, HttpResponse.BodyHandlers.discarding()).statusCode();
	}

	// Privatni ključ je samo broj d na krivoj P-256 (32 bajta). JCA od njega i parametara krive pravi ključ.
	private static ECPrivateKey privatni(String base64url) {
		try {
			AlgorithmParameters parametri = AlgorithmParameters.getInstance("EC");
			parametri.init(new ECGenParameterSpec("secp256r1"));
			ECParameterSpec kriva = parametri.getParameterSpec(ECParameterSpec.class);
			BigInteger d = new BigInteger(1, Base64.getUrlDecoder().decode(base64url));
			return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(d, kriva));
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			throw new IllegalStateException("VAPID_PRIVATE_KEY nije ispravan P-256 ključ u base64url obliku", e);
		}
	}

}
