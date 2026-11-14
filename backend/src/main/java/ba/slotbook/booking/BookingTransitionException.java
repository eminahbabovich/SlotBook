package ba.slotbook.booking;

public class BookingTransitionException extends RuntimeException {

	private final BookingStatus from;
	private final BookingStatus to;

	public BookingTransitionException(BookingStatus from, BookingStatus to) {
		super("Prelaz " + from + " -> " + to + " nije dozvoljen");
		this.from = from;
		this.to = to;
	}

	public BookingStatus getFrom() {
		return from;
	}

	public BookingStatus getTo() {
		return to;
	}

}
