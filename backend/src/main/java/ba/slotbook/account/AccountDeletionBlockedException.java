package ba.slotbook.account;

import java.util.List;

public class AccountDeletionBlockedException extends RuntimeException {

	private final List<String> organizacije;
	private final List<String> resursi;

	AccountDeletionBlockedException(List<String> organizacije, List<String> resursi) {
		super("Nalog se ne može obrisati");
		this.organizacije = organizacije;
		this.resursi = resursi;
	}

	// Organizacije u kojima je korisnik jedini admin
	public List<String> getOrganizacije() {
		return organizacije;
	}

	// Resursi koje vodi, i neaktivni
	public List<String> getResursi() {
		return resursi;
	}

}
