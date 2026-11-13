package ba.slotbook.error;

// Greška u jednom polju koju Bean Validation ne može uhvatiti jer traži podatke iz baze ili
// poređenje više polja. Handler je vraća u istom obliku kao validaciju, pa frontend ima jedan
// način prikaza. Poruka je namjerno tekst za korisnika, odvojen od getMessage().
public class InvalidFieldException extends RuntimeException {

	private final String polje;
	private final String poruka;

	public InvalidFieldException(String polje, String poruka) {
		super("Neispravno polje " + polje);
		this.polje = polje;
		this.poruka = poruka;
	}

	public String getPolje() {
		return polje;
	}

	public String getPoruka() {
		return poruka;
	}

}
