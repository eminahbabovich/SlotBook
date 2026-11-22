package ba.slotbook.booking;

import java.time.Instant;
import java.util.List;

// Bar jedna sedmica serije nije slobodna. Nijedna rezervacija nije napravljena.
public class SeriesConflictException extends RuntimeException {

	private final List<Instant> nisuSlobodni;

	public SeriesConflictException(List<Instant> nisuSlobodni) {
		super("Termini serije nisu slobodni: " + nisuSlobodni);
		this.nisuSlobodni = List.copyOf(nisuSlobodni);
	}

	public List<Instant> getNisuSlobodni() {
		return nisuSlobodni;
	}

}
