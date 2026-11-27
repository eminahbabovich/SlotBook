package ba.slotbook.chat;

import java.util.UUID;

import ba.slotbook.booking.BookingStatus;

// action je BOOK ili CANCEL. bookingStatus kaže da li rezervacija čeka potvrdu providera (PENDING).
public record ChatProposalResponse(UUID id, String action, UUID bookingId, BookingStatus bookingStatus) {
}
