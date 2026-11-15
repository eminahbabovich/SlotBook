package ba.slotbook.stats;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import ba.slotbook.booking.BookingStatus;

// from i to su datumi u zoni organizacije, to se ne uključuje, isto kao kod /slots.
// byStatus uvijek ima svih sedam statusa, i one s nulom.
public record StatsResponse(LocalDate from, LocalDate to, String timezone, Map<BookingStatus, Long> byStatus,
		List<DayHourCount> byDayAndHour, List<ResourceOccupancy> occupancy) {
}
