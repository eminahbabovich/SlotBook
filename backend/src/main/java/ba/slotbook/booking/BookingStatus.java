package ba.slotbook.booking;

import java.util.EnumSet;
import java.util.Set;

public enum BookingStatus {
	PENDING,
	CONFIRMED,
	REJECTED,
	EXPIRED,
	CANCELLED,
	COMPLETED,
	NO_SHOW;

	// Ista lista stoji u WHERE dijelu EXCLUDE constrainta (V10). Ostala stanja oslobađaju termin.
	static final Set<BookingStatus> DRZE_TERMIN = EnumSet.of(PENDING, CONFIRMED);
}
