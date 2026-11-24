package ba.slotbook.payment;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;

@Service
public class StripeWebhookService {

	// Stripeova preporuka: potpis stariji od 5 minuta se odbija, da snimljen zahtjev ne može doći ponovo
	static final long TOLERANCIJA_SEKUNDI = 300;

	private final PaymentService paymentService;
	private final String tajna;
	private final Clock clock;

	public StripeWebhookService(PaymentService paymentService,
			@Value("${slotbook.stripe.webhook-secret}") String tajna, Clock clock) {
		this.paymentService = paymentService;
		this.tajna = tajna;
		this.clock = clock;
	}

	// Ruta je javna, pa je potpis jedini dokaz da je zahtjev poslao Stripe. Provjerava se na tijelu
	// tačno kako je stiglo, prije čitanja JSON-a, pa do čitanja dođe samo ono što je Stripe potpisao.
	// Bez tajne (prazna) svaki zahtjev pada.
	public void primi(String tijelo, String potpis) {
		Event dogadjaj;
		try {
			dogadjaj = Webhook.constructEvent(tijelo, potpis == null ? "" : potpis, tajna, TOLERANCIJA_SEKUNDI, clock);
		} catch (SignatureVerificationException e) {
			throw new InvalidWebhookException(e);
		}
		if (!dogadjaj.getType().equals(StripeDogadjaj.PLACENO) && !dogadjaj.getType().equals(StripeDogadjaj.ISTEKLO)) {
			return;
		}
		Session sesija;
		try {
			sesija = (Session) dogadjaj.getDataObjectDeserializer().deserializeUnsafe();
		} catch (EventDataObjectDeserializationException e) {
			throw new InvalidWebhookException(e);
		}
		paymentService.obradi(new StripeDogadjaj(dogadjaj.getId(), dogadjaj.getType(), sesija.getId(),
				sesija.getPaymentIntent(), "paid".equals(sesija.getPaymentStatus())));
	}

}
