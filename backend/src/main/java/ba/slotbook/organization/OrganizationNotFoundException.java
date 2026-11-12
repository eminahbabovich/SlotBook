package ba.slotbook.organization;

public class OrganizationNotFoundException extends RuntimeException {

	public OrganizationNotFoundException(String slug) {
		super("Organizacija " + slug + " ne postoji");
	}

}
