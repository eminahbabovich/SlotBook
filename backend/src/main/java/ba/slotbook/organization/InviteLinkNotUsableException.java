package ba.slotbook.organization;

public class InviteLinkNotUsableException extends RuntimeException {

	public enum Reason {
		NOT_FOUND, REVOKED, EXPIRED, FULL, EMAIL_NOT_VERIFIED
	}

	private final Reason reason;

	public InviteLinkNotUsableException(Reason reason) {
		super("Pozivni link se ne može iskoristiti: " + reason);
		this.reason = reason;
	}

	public Reason getReason() {
		return reason;
	}

}
