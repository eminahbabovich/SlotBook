package ba.slotbook.availability;

import java.util.List;

// Termini su u UTC-u (Instant), a zona ide uz njih da frontend zna u kojoj ih prikazati.
// capacity je broj mjesta u svakom terminu (1 za običan resurs).
public record SlotsResponse(String timezone, int capacity, List<TimeRange> slots) {
}
