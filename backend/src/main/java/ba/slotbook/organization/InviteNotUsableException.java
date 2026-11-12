package ba.slotbook.organization;

public class InviteNotUsableException extends RuntimeException {

	public enum Reason {
		NOT_FOUND, OTHER_EMAIL, ALREADY_USED, EXPIRED
	}

	private final Reason reason;

	public InviteNotUsableException(Reason reason) {
		super("Pozivnica se ne može iskoristiti: " + reason);
		this.reason = reason;
	}

	public Reason getReason() {
		return reason;
	}

}
