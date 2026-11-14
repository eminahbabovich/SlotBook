package ba.slotbook.notification;

// Ime kaže šta se desilo, a ne kome ide. Primaoca bira BookingService.
public enum NotificationType {
	BOOKING_AWAITS_CONFIRMATION, BOOKING_CONFIRMED, BOOKING_REJECTED, BOOKING_CANCELLED, BOOKING_EXPIRED,
	EMAIL_VERIFICATION
}
