package ba.slotbook.notification;

import java.io.IOException;
import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.error.InvalidFieldException;
import ba.slotbook.notification.PushException.Razlog;
import ba.slotbook.user.UserRepository;
import tools.jackson.databind.json.JsonMapper;

// Pretplate se vežu za korisnika iz tokena. Svaki upit traži i endpoint i korisnika, pa niko ne može
// ugasiti ni testirati tuđi browser, čak ni kad zna njegov endpoint.
@Service
public class PushSubscriptionService {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final PushSubscriptionRepository pretplate;
	private final UserRepository users;
	private final WebPushClient webPush;
	private final Clock clock;

	public PushSubscriptionService(PushSubscriptionRepository pretplate, UserRepository users, WebPushClient webPush,
			Clock clock) {
		this.pretplate = pretplate;
		this.users = users;
		this.webPush = webPush;
		this.clock = clock;
	}

	public PushKeyResponse publicKey() {
		return new PushKeyResponse(webPush.ukljucen(), webPush.javniKljuc());
	}

	@Transactional
	public PushSubscriptionResponse save(UUID userId, PushSubscriptionRequest zahtjev) {
		if (!webPush.ukljucen()) {
			throw new PushException(Razlog.ISKLJUCEN);
		}
		if (!WebPushClient.poznatServis(zahtjev.endpoint())) {
			throw new InvalidFieldException("endpoint", "Ovo nije adresa poznatog push servisa.");
		}
		var korisnik = users.getReferenceById(userId);
		String uredjaj = zahtjev.device() == null || zahtjev.device().isBlank() ? null : zahtjev.device().strip();
		PushSubscription pretplata = pretplate.findByEndpoint(zahtjev.endpoint())
				.map(p -> {
					p.preuzmi(korisnik, zahtjev.keys().p256dh(), zahtjev.keys().auth(), uredjaj, clock.instant());
					return p;
				})
				.orElseGet(() -> pretplate.save(new PushSubscription(korisnik, zahtjev.endpoint(),
						zahtjev.keys().p256dh(), zahtjev.keys().auth(), uredjaj, clock.instant())));
		return new PushSubscriptionResponse(pretplata.getId(), pretplata.getDevice(), pretplata.getCreatedAt());
	}

	// Isključivanje je idempotentno: ako pretplate nema, nema ni šta brisati
	@Transactional
	public void delete(UUID userId, String endpoint) {
		pretplate.findByEndpointAndUserId(endpoint, userId).ifPresent(pretplate::delete);
	}

	// Probna obavijest ide odmah, mimo reda čekanja, da se na dugme vidi da li radi. Zato nema
	// ponovnih pokušaja: ako ne prođe, korisnik to vidi i klikne ponovo.
	public void sendTest(UUID userId, String endpoint) {
		if (!webPush.ukljucen()) {
			throw new PushException(Razlog.ISKLJUCEN);
		}
		PushSubscription pretplata = pretplate.findByEndpointAndUserId(endpoint, userId)
				.orElseThrow(() -> new PushException(Razlog.NEMA_PRETPLATE));
		String poruka = JSON.writeValueAsString(new PushMessage("Probna obavijest",
				"Ovako izgledaju obavijesti SlotBooka na ovom uređaju.", "/nalog"));
		int status;
		try {
			status = webPush.posalji(pretplata, poruka);
		} catch (IOException e) {
			throw new PushException(Razlog.SERVIS_ODBIO);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new PushException(Razlog.SERVIS_ODBIO);
		}
		if (status == 404 || status == 410) {
			pretplate.ukloni(pretplata.getId());
			throw new PushException(Razlog.NEMA_PRETPLATE);
		}
		if (status < 200 || status >= 300) {
			throw new PushException(Razlog.SERVIS_ODBIO);
		}
	}

}
