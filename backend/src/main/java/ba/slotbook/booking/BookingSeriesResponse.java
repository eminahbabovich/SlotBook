package ba.slotbook.booking;

import java.util.List;
import java.util.UUID;

// Rezervacije serije, po redu sedmica. Svaka ima svoj status i otkazuje se posebno.
public record BookingSeriesResponse(UUID seriesId, List<BookingResponse> bookings) {
}
