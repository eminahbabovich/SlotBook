package ba.slotbook.organization;

public class InviteRaceException extends RuntimeException {

	public InviteRaceException() {
		super("Druga pozivnica za isti email upisana u istom trenutku");
	}

}
