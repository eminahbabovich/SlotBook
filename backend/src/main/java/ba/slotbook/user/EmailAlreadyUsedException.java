package ba.slotbook.user;

public class EmailAlreadyUsedException extends RuntimeException {

	public EmailAlreadyUsedException() {
		super("Email je već registrovan");
	}

}
