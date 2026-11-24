package ba.slotbook.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.stripe.param.checkout.SessionCreateParams;

// Šta tačno ide Stripeu, bez mreže. Pravi poziv se provjerava ručno sa sandbox ključem.
class StripeCheckoutClientTest {

	private static final UUID REZERVACIJA = UUID.fromString("5f0c2a8e-3b1d-4c6e-9a7f-1d2e3f4a5b6c");

	@Test
	void stripeDobijaIznosUFeninzimaValutuBamIRokUSekundama() {
		var nova = new StripeCheckout.NovaSesija("kljuc-1", REZERVACIJA, "Sala za jogu, 19.11.2035. u 09:00", 1500,
				"bam", Instant.parse("2035-11-12T10:31:00Z"), "http://localhost:5173/rezervacije?uplata=uspjeh",
				"http://localhost:5173/rezervacije?uplata=odustao");

		SessionCreateParams parametri = StripeCheckoutClient.parametri(nova);

		assertThat(parametri.getMode()).isEqualTo(SessionCreateParams.Mode.PAYMENT);
		assertThat(parametri.getClientReferenceId()).isEqualTo(REZERVACIJA.toString());
		assertThat(parametri.getMetadata()).containsEntry("bookingId", REZERVACIJA.toString());
		assertThat(parametri.getExpiresAt()).isEqualTo(Instant.parse("2035-11-12T10:31:00Z").getEpochSecond());
		assertThat(parametri.getLineItems()).singleElement().satisfies(stavka -> {
			assertThat(stavka.getQuantity()).isEqualTo(1L);
			assertThat(stavka.getPriceData().getCurrency()).isEqualTo("bam");
			assertThat(stavka.getPriceData().getUnitAmount()).isEqualTo(1500L);
			assertThat(stavka.getPriceData().getProductData().getName()).isEqualTo("Sala za jogu, 19.11.2035. u 09:00");
		});
	}

}
