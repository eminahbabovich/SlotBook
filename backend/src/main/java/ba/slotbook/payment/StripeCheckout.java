package ba.slotbook.payment;

import java.time.Instant;
import java.util.UUID;

// Interface ima dvije implementacije: pravi Stripe i lažni u testovima, da CI ne zavisi od Stripeovog
// servera i da test može reći "Stripe ne radi". Kao StorageService (lokalno i S3).
public interface StripeCheckout {

	Sesija otvori(NovaSesija nova);

	// Cijeli iznos nazad klijentu. Isti ključ drugi put ne vraća novac ponovo.
	void vrati(String paymentIntent, String kljuc);

	// Sesija se više ne može platiti. Ne uspije ako je klijent upravo platio, i tada uplatu vrati webhook.
	void zatvori(String sessionId);

	// kljuc ide Stripeu kao Idempotency-Key: ponovljen poziv s istim ključem vrati istu sesiju
	record NovaSesija(String kljuc, UUID bookingId, String naziv, int iznosFeninga, String valuta,
			Instant istice, String povratakUspjeh, String povratakOdustao) {
	}

	record Sesija(String id, String url, Instant expiresAt) {
	}

	// Stripe nije odgovorio ili je odbio zahtjev. Poruka Stripea ide u log, ne klijentu.
	class NedostupanException extends RuntimeException {

		public NedostupanException(String poruka, Throwable uzrok) {
			super(poruka, uzrok);
		}

	}

}
