package ba.slotbook.booking;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import ba.slotbook.availability.TimeRange;

// Kalendar resursa: slobodni termini i oni koje drži rezervacija. Termini su u UTC-u, a zona ide
// uz njih da frontend zna u kojoj ih prikazati. offeredToMe su termini iz slots koje lista čekanja
// trenutno drži samo za mene.
public record SlotCalendarResponse(String timezone, List<TimeRange> slots, List<TakenSlot> taken,
		List<OfferedSlot> offeredToMe) {

	// myStatus je null kad rezervacija nije tvoja: drugi vide samo da je termin zauzet,
	// ne čiji je ni da li čeka potvrdu. myWaitlistId je moj upis na listu čekanja za taj termin.
	public record TakenSlot(Instant start, Instant end, BookingStatus myStatus, UUID myWaitlistId) {
	}

	public record OfferedSlot(Instant start, Instant end, Instant offeredUntil, UUID waitlistId) {
	}

}
