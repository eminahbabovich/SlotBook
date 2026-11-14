package ba.slotbook.auth;

public class UserTokenException extends RuntimeException {

	public enum Reason {
		NOT_FOUND, ALREADY_USED, EXPIRED
	}

	private final UserTokenType type;
	private final Reason reason;

	UserTokenException(UserTokenType type, Reason reason) {
		super("Link iz maila ne važi: " + type + ", " + reason);
		this.type = type;
		this.reason = reason;
	}

	public UserTokenType getType() {
		return type;
	}

	public Reason getReason() {
		return reason;
	}

}
