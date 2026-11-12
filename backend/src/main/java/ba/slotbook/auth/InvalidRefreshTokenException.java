package ba.slotbook.auth;

public class InvalidRefreshTokenException extends RuntimeException {

	public InvalidRefreshTokenException() {
		super("Refresh token ne postoji, istekao je ili je već iskorišten");
	}

}
