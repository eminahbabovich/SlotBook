package ba.slotbook.booking;

public class WaitlistNotNeededException extends RuntimeException {

	public enum Razlog {
		SLOBODAN, TVOJ
	}

	private final Razlog razlog;

	WaitlistNotNeededException(Razlog razlog) {
		super("Lista čekanja ne treba: " + razlog);
		this.razlog = razlog;
	}

	public Razlog getRazlog() {
		return razlog;
	}

}
