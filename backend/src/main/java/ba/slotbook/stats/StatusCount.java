package ba.slotbook.stats;

import ba.slotbook.booking.BookingStatus;

public record StatusCount(BookingStatus status, long count) {
}
