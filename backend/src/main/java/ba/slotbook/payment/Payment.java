package ba.slotbook.payment;

import java.time.Instant;
import java.util.UUID;

import ba.slotbook.booking.Booking;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "payments")
public class Payment {

	// ID pravi Java, a ne baza, jer ide Stripeu kao idempotency ključ prije nego što je red upisan
	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_id", updatable = false)
	private Booking booking;

	@Column(name = "amount_cents", nullable = false, updatable = false)
	private int amountCents;

	@Column(nullable = false, updatable = false)
	private String currency;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentStatus status;

	@Column(name = "stripe_session_id", nullable = false, updatable = false)
	private String stripeSessionId;

	@Column(name = "checkout_url", nullable = false, updatable = false)
	private String checkoutUrl;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Payment() {
	}

	Payment(UUID id, Booking booking, String currency, StripeCheckout.Sesija sesija, Instant createdAt) {
		this.id = id;
		this.booking = booking;
		this.amountCents = booking.getPriceCents();
		this.currency = currency;
		this.status = PaymentStatus.OPEN;
		this.stripeSessionId = sesija.id();
		this.checkoutUrl = sesija.url();
		this.expiresAt = sesija.expiresAt();
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public PaymentStatus getStatus() {
		return status;
	}

	public String getCheckoutUrl() {
		return checkoutUrl;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

}
