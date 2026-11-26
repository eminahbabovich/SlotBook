package ba.slotbook.payment;

// Isti spisak kao CHECK payments_status (V40). OPEN nastaje s Checkout sesijom, PAID postavi
// webhook, EXPIRED istek sesije, a REFUNDED povrat pri otkazivanju.
public enum PaymentStatus {
	OPEN,
	PAID,
	EXPIRED,
	REFUNDED
}
