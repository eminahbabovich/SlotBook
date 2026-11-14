package ba.slotbook.booking;

public class NoShowTooEarlyException extends RuntimeException {

	public NoShowTooEarlyException() {
		super("Nedolazak prije početka termina");
	}

}
