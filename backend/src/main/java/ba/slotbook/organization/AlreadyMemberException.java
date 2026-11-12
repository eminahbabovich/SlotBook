package ba.slotbook.organization;

public class AlreadyMemberException extends RuntimeException {

	public AlreadyMemberException() {
		super("Korisnik je već član organizacije");
	}

}
