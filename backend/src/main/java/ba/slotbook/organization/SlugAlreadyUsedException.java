package ba.slotbook.organization;

public class SlugAlreadyUsedException extends RuntimeException {

	public SlugAlreadyUsedException(String slug) {
		super("Adresa " + slug + " je zauzeta");
	}

}
