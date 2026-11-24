package ba.slotbook.payment;

// Ono što obrada treba iz Stripe događaja o Checkout sesiji, bez Stripeovih klasa. Tako PaymentService
// ne zavisi od biblioteke, a test servisa ne mora praviti Stripe JSON.
record StripeDogadjaj(String id, String tip, String sessionId, String paymentIntent, boolean placeno) {

	static final String PLACENO = "checkout.session.completed";
	static final String ISTEKLO = "checkout.session.expired";

}
