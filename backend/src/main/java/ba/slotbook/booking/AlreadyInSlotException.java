package ba.slotbook.booking;

// Osoba već ima mjesto u ovom terminu. U grupnom terminu ima još mjesta, ali drugo ne može uzeti ista osoba.
public class AlreadyInSlotException extends RuntimeException {

	public AlreadyInSlotException() {
		super("Osoba već ima mjesto u terminu");
	}

}
