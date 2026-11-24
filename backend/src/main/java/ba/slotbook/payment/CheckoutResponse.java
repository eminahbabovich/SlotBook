package ba.slotbook.payment;

import java.time.Instant;

// Adresa Stripe Checkout stranice, na koju frontend preusmjeri klijenta, i do kada važi
public record CheckoutResponse(String url, Instant expiresAt) {

	static CheckoutResponse from(Payment uplata) {
		return new CheckoutResponse(uplata.getCheckoutUrl(), uplata.getExpiresAt());
	}

}
