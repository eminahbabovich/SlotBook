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

	// Tabela prelaza iz PROJECT_PLAN.md. Završna stanja nemaju izlaz, pa otkazana rezervacija ne
	// može ponovo postati potvrđena i zauzeti termin koji je u međuvremenu možda dobio neko drugi.
	boolean mozePreciU(BookingStatus novi) {
		return switch (this) {
			case PENDING -> EnumSet.of(CONFIRMED, REJECTED, EXPIRED, CANCELLED).contains(novi);
			case CONFIRMED -> EnumSet.of(CANCELLED, COMPLETED, NO_SHOW).contains(novi);
			case REJECTED, EXPIRED, CANCELLED, COMPLETED, NO_SHOW -> false;
		};
	}
}
