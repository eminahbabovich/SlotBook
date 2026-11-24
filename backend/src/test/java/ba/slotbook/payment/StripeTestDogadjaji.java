package ba.slotbook.payment;

import java.time.Instant;

import com.stripe.net.Webhook;

// Stripe događaji o Checkout sesiji za testove, potpisani kao što ih potpisuje Stripe
final class StripeTestDogadjaji {

	// Ista kao slotbook.stripe.webhook-secret u TestcontainersConfiguration
	static final String TAJNA = "whsec_testna_tajna_samo_za_testove";

	record Potpisan(String tijelo, String potpis) {
	}

	private StripeTestDogadjaji() {
	}

	// Samo polja koja čitamo, u obliku u kojem ih Stripe šalje
	static String dogadjaj(String id, String tip, String sesija, String paymentIntent, String placeno) {
		return """
				{"id": "%s", "object": "event", "type": "%s", "api_version": "2025-01-01", "created": 1,
				 "data": {"object": {"id": "%s", "object": "checkout.session", "payment_intent": %s,
				  "payment_status": "%s", "amount_total": 1500, "currency": "bam"}}}
				""".formatted(id, tip, sesija, paymentIntent == null ? "null" : "\"" + paymentIntent + "\"", placeno);
	}

	static Potpisan potpisan(String tijelo) throws Exception {
		return potpisan(tijelo, Instant.now());
	}

	// Kao Stripe: HMAC-SHA256 nad "vrijeme.tijelo" tajnom webhooka
	static Potpisan potpisan(String tijelo, Instant kad) throws Exception {
		long t = kad.getEpochSecond();
		return new Potpisan(tijelo, "t=" + t + ",v1=" + Webhook.Util.computeHmacSha256(TAJNA, t + "." + tijelo));
	}

}
