package ba.slotbook.payment;

import java.time.Instant;
import java.util.UUID;

// Interface ima dvije implementacije: pravi Stripe i lažni u testovima, da CI ne zavisi od Stripeovog
// servera i da test može reći "Stripe ne radi". Kao StorageService (lokalno i S3).
public interface StripeCheckout {

	Sesija otvori(NovaSesija nova);

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
