package ba.slotbook.payment;

// Potpis ne odgovara, prestar je, ili tijelo nije Stripe događaj. Uzrok ide u log, ne pošiljaocu.
public class InvalidWebhookException extends RuntimeException {

	public InvalidWebhookException(Throwable uzrok) {
		super("Neispravan Stripe webhook", uzrok);
	}

}
