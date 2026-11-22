package ba.slotbook.booking;

import java.util.EnumSet;
import java.util.Set;

// WAITING čeka da se termin oslobodi, OFFERED ga ima do offeredUntil. Ostala stanja su završna.
public enum WaitlistStatus {
	WAITING,
	OFFERED,
	BOOKED,
	EXPIRED,
	CANCELLED;

	// Ista lista stoji u WHERE dijelu indeksa waitlist_jednom_po_terminu (V34)
	static final Set<WaitlistStatus> ZIVI = EnumSet.of(WAITING, OFFERED);
}
