package ba.slotbook.booking;

// Termin drže samo PENDING i CONFIRMED. Ista lista stoji u WHERE dijelu EXCLUDE constrainta (V10).
public enum BookingStatus {
	PENDING,
	CONFIRMED,
	REJECTED,
	EXPIRED,
	CANCELLED,
	COMPLETED,
	NO_SHOW
}
