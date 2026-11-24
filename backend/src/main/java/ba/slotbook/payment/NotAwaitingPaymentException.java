package ba.slotbook.payment;

// Rezervacija je besplatna, već plaćena, otkazana ili joj je prošao rok za uplatu
public class NotAwaitingPaymentException extends RuntimeException {

	public NotAwaitingPaymentException() {
		super("Rezervacija ne čeka uplatu");
	}

}
