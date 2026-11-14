package ba.slotbook.auth;

public class VerificationTokenException extends RuntimeException {

	public enum Reason {
		NOT_FOUND, ALREADY_USED, EXPIRED
	}

	private final Reason reason;

	VerificationTokenException(Reason reason) {
		super("Link za potvrdu emaila ne važi: " + reason);
		this.reason = reason;
	}

	public Reason getReason() {
		return reason;
	}

}
