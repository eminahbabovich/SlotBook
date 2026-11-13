package ba.slotbook.availability;

import java.time.Instant;

// Period od start do end, s isključenim krajem, isto kao tstzrange u bazi: termin 09:00-10:00
// i izuzetak od 10:00 se dodiruju, ali ne preklapaju
public record TimeRange(Instant start, Instant end) {

	boolean overlaps(TimeRange drugi) {
		return start.isBefore(drugi.end) && drugi.start.isBefore(end);
	}

}
