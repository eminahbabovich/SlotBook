package ba.slotbook.availability;

public class TimeOffNotFoundException extends RuntimeException {

	public TimeOffNotFoundException() {
		super("Izuzetak ne postoji za ovaj resurs");
	}

}
