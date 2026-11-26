package ba.slotbook.organization;

public class MemberNotFoundException extends RuntimeException {

	public MemberNotFoundException() {
		super("Član ne postoji u organizaciji");
	}

}
