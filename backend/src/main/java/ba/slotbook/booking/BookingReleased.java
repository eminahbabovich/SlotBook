package ba.slotbook.booking;

import java.util.UUID;

// Rezervacija više ne drži termin: otkazana, odbijena ili istekla. Plaćanje (ba.slotbook.payment) po
// tome vraća novac ili zatvara Checkout. Događaj, a ne poziv, jer plaćanje već zavisi od rezervacija,
// pa bi direktan poziv napravio krug između BookingService i PaymentService.
public record BookingReleased(UUID bookingId) {
}
