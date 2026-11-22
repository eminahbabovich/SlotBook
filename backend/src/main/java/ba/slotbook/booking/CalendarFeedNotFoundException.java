package ba.slotbook.booking;

public class CalendarFeedNotFoundException extends RuntimeException {

	public CalendarFeedNotFoundException() {
		super("Link za kalendar ne postoji");
	}

}
