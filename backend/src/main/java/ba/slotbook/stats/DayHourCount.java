package ba.slotbook.stats;

// dayOfWeek po ISO-u: 1 je ponedjeljak, 7 nedjelja, isto kao java.time.DayOfWeek.of(...).
// Dan i sat su lokalni, u zoni organizacije.
public record DayHourCount(int dayOfWeek, int hour, long count) {
}
