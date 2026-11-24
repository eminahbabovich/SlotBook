package ba.slotbook.payment;

// Isti spisak kao CHECK payments_status (V40). OPEN pravi korak 2, PAID webhook u koraku 3,
// a EXPIRED i REFUNDED istek i povrat u koracima 3 i 4.
public enum PaymentStatus {
	OPEN,
	PAID,
	EXPIRED,
	REFUNDED
}
