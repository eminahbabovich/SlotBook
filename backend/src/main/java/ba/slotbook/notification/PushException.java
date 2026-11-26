package ba.slotbook.notification;

public class PushException extends RuntimeException {

	public enum Razlog {
		// Server nema VAPID ključeve
		ISKLJUCEN,
		// Ovaj browser nije prijavljen za obavijesti ovog korisnika, ili ga je push servis odjavio
		NEMA_PRETPLATE,
		// Push servis nije primio poruku ili se nije javio
		SERVIS_ODBIO
	}

	private final Razlog razlog;

	public PushException(Razlog razlog) {
		super("Push: " + razlog);
		this.razlog = razlog;
	}

	public Razlog getRazlog() {
		return razlog;
	}

}
