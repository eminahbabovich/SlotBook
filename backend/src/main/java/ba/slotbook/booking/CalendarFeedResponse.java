package ba.slotbook.booking;

import java.time.Instant;

// token stiže samo u odgovoru na POST, kad je link upravo napravljen. Inače je null.
public record CalendarFeedResponse(boolean active, Instant createdAt, String token) {
}
