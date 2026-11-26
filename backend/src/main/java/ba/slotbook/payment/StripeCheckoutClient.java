package ba.slotbook.payment;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionCreateParams.LineItem;
import com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData;
import com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData.ProductData;

@Component
class StripeCheckoutClient implements StripeCheckout {

	private final StripeClient stripe;

	// Bez ključa (CI, lokalno bez .env) aplikacija se ipak pokrene, a prvi poziv Stripea padne s 503.
	// Timeoutovi su kraći od podrazumijevanih 80 s, jer za to vrijeme rezervacija stoji zaključana.
	// Ponovni pokušaj je siguran samo zato što svaki zahtjev nosi idempotency ključ.
	StripeCheckoutClient(@Value("${slotbook.stripe.secret-key}") String kljuc) {
		this.stripe = StripeClient.builder()
				.setApiKey(kljuc)
				.setConnectTimeout(5_000)
				.setReadTimeout(20_000)
				.setMaxNetworkRetries(2)
				.build();
	}

	@Override
	public Sesija otvori(NovaSesija nova) {
		try {
			Session sesija = stripe.v1().checkout().sessions().create(parametri(nova),
					RequestOptions.builder().setIdempotencyKey(nova.kljuc()).build());
			return new Sesija(sesija.getId(), sesija.getUrl(), Instant.ofEpochSecond(sesija.getExpiresAt()));
		} catch (StripeException e) {
			throw new NedostupanException("Stripe nije otvorio Checkout sesiju: " + e.getMessage(), e);
		}
	}

	@Override
	public void vrati(String paymentIntent, String kljuc) {
		try {
			stripe.v1().refunds().create(RefundCreateParams.builder().setPaymentIntent(paymentIntent).build(),
					RequestOptions.builder().setIdempotencyKey(kljuc).build());
		} catch (StripeException e) {
			throw new NedostupanException("Stripe nije vratio novac: " + e.getMessage(), e);
		}
	}

	@Override
	public void zatvori(String sessionId) {
		try {
			stripe.v1().checkout().sessions().expire(sessionId);
		} catch (StripeException e) {
			throw new NedostupanException("Stripe nije zatvorio sesiju: " + e.getMessage(), e);
		}
	}

	// Posebno od poziva, da test bez mreže provjeri šta se šalje Stripeu
	static SessionCreateParams parametri(NovaSesija nova) {
		return SessionCreateParams.builder()
				.setMode(SessionCreateParams.Mode.PAYMENT)
				// Webhook po ovome nalazi rezervaciju i kad naš red uplate još nije commitan
				.setClientReferenceId(nova.bookingId().toString())
				.putMetadata("bookingId", nova.bookingId().toString())
				.setExpiresAt(nova.istice().getEpochSecond())
				.setSuccessUrl(nova.povratakUspjeh())
				.setCancelUrl(nova.povratakOdustao())
				.addLineItem(LineItem.builder()
						.setQuantity(1L)
						.setPriceData(PriceData.builder()
								.setCurrency(nova.valuta())
								.setUnitAmount((long) nova.iznosFeninga())
								.setProductData(ProductData.builder().setName(nova.naziv()).build())
								.build())
						.build())
				.build();
	}

}
