package ba.slotbook.availability;

import java.util.List;

// Termini su u UTC-u (Instant), a zona ide uz njih da frontend zna u kojoj ih prikazati
public record SlotsResponse(String timezone, List<TimeRange> slots) {
}
