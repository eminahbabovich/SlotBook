package ba.slotbook.payment;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

// Stripe u testovima: pamti šta mu je poslano, može kasniti i može "ne raditi". Kontekst Springa
// dijele svi testovi, pa test traži svoje pozive po rezervaciji.
public class LazniStripeCheckout implements StripeCheckout {

	private final List<NovaSesija> pozivi = new CopyOnWriteArrayList<>();
	private volatile boolean neRadi;
	private volatile Duration kasni = Duration.ZERO;

	@Override
	public Sesija otvori(NovaSesija nova) {
		pozivi.add(nova);
		try {
			Thread.sleep(kasni);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		if (neRadi) {
			throw new NedostupanException("Lažni Stripe ne radi", null);
		}
		String id = "cs_test_" + nova.kljuc();
		return new Sesija(id, "https://checkout.stripe.com/c/pay/" + id, nova.istice());
	}

	public List<NovaSesija> poziviZa(UUID bookingId) {
		return pozivi.stream().filter(poziv -> poziv.bookingId().equals(bookingId)).toList();
	}

	public void neRadi(boolean neRadi) {
		this.neRadi = neRadi;
	}

	public void kasni(Duration kasni) {
		this.kasni = kasni;
	}

}
