package ba.slotbook.booking;

public class SlotTakenException extends RuntimeException {

	public SlotTakenException() {
		super("Termin je zauzet");
	}

}
