package ba.slotbook.booking;

// Plaćenu rezervaciju potvrđuje uplata, ne provider. Odbiti je provider i dalje može.
public class AwaitingPaymentException extends RuntimeException {

	public AwaitingPaymentException() {
		super("Rezervacija čeka uplatu");
	}

}
