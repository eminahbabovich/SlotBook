package ba.slotbook.organization;

public class LastAdminException extends RuntimeException {

	public LastAdminException() {
		super("Organizacija bi ostala bez admina");
	}

}
