package ba.slotbook.booking;

public class SlotOfferedException extends RuntimeException {

	public SlotOfferedException() {
		super("Termin je ponuđen nekome s liste čekanja");
	}

}
