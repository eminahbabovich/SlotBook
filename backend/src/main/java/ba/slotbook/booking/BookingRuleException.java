package ba.slotbook.booking;

import java.time.Instant;

// Rezervacija krši pravilo korištenja resursa ili organizacije. Poruka je već na bosanskom i sa
// datumom u zoni organizacije, jer je slaže BookingRules koji zonu zna.
public class BookingRuleException extends RuntimeException {

	public enum Pravilo {
		BLOCKED, MAX_ACTIVE, TOO_FAR_AHEAD, TOO_SOON
	}

	private final Pravilo pravilo;
	private final String poruka;
	private final Instant blokiranDo;

	BookingRuleException(Pravilo pravilo, String poruka, Instant blokiranDo) {
		super("Pravilo rezervacije: " + pravilo);
		this.pravilo = pravilo;
		this.poruka = poruka;
		this.blokiranDo = blokiranDo;
	}

	public Pravilo getPravilo() {
		return pravilo;
	}

	public String getPoruka() {
		return poruka;
	}

	public Instant getBlokiranDo() {
		return blokiranDo;
	}

}
