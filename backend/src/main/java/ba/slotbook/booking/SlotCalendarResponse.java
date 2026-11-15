package ba.slotbook.booking;

import java.time.Instant;
import java.util.List;

import ba.slotbook.availability.TimeRange;

// Kalendar resursa: slobodni termini i oni koje drži rezervacija. Termini su u UTC-u, a zona ide
// uz njih da frontend zna u kojoj ih prikazati.
public record SlotCalendarResponse(String timezone, List<TimeRange> slots, List<TakenSlot> taken) {

	// myStatus je null kad rezervacija nije tvoja: drugi vide samo da je termin zauzet,
	// ne čiji je ni da li čeka potvrdu.
	public record TakenSlot(Instant start, Instant end, BookingStatus myStatus) {
	}

}
