package ba.slotbook.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import ba.slotbook.booking.BookingReleased;

// Šta plaćanje radi kad rezervacija pusti termin. Dva različita trenutka, jer povrat mora uspjeti
// zajedno s otkazivanjem, a zatvaranje sesije je samo pristojnost prema klijentu.
@Component
class PaymentListener {

	private static final Logger log = LoggerFactory.getLogger(PaymentListener.class);

	private final PaymentService paymentService;

	PaymentListener(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	// Sinhrono, u transakciji onoga ko je otkazao. Izuzetak ovdje poništi i otkazivanje.
	@EventListener
	void vratiNovac(BookingReleased dogadjaj) {
		paymentService.refundReleased(dogadjaj.bookingId());
	}

	// Poslije commita, pa Stripe ne usporava ni ne obara otkazivanje, istek ni novu rezervaciju
	// (istek se dešava i pod lockom resursa, kad neko rezerviše).
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void zatvoriSesiju(BookingReleased dogadjaj) {
		try {
			paymentService.closeOpenCheckout(dogadjaj.bookingId());
		} catch (RuntimeException e) {
			// Otkazivanje je već commitano i klijent ovo ne vidi ni bez catch: Spring izuzetak iz AFTER_COMMIT
			// sam uhvati i zapiše kao ERROR (TransactionSynchronizationUtils). Ovdje je WARN, jer je to
			// očekivan slučaj (npr. sesija plaćena sekundu ranije), a ne greška aplikacije.
			log.warn("Checkout sesija za rezervaciju {} nije zatvorena, uplatu će vratiti webhook ako stigne",
					dogadjaj.bookingId(), e);
		}
	}

}
