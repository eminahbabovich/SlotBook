package ba.slotbook.notification;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import ba.slotbook.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "notifications")
public class Notification {

	static final int NAJVISE_POKUSAJA = 5;
	private static final int NAJDUZA_GRESKA = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", updatable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private NotificationType type;

	// Samo ID, ne veza na Booking: paket booking zna za obavijesti, a obrnuto bi bio krug
	@Column(name = "booking_id", updatable = false)
	private UUID bookingId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private NotificationStatus status;

	@Column(nullable = false)
	private int attempts;

	@Column(name = "next_attempt_at", nullable = false)
	private Instant nextAttemptAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "sent_at")
	private Instant sentAt;

	@Column(nullable = false, updatable = false)
	private String subject;

	@Column(nullable = false, updatable = false)
	private String body;

	@Column(name = "last_error")
	private String lastError;

	protected Notification() {
	}

	Notification(User user, NotificationType type, UUID bookingId, EmailContent content, Instant createdAt) {
		this.user = user;
		this.type = type;
		this.bookingId = bookingId;
		this.subject = content.subject();
		this.body = content.body();
		this.status = NotificationStatus.PENDING;
		this.nextAttemptAt = createdAt;
		this.createdAt = createdAt;
	}

	void poslana(Instant sada) {
		attempts++;
		status = NotificationStatus.SENT;
		sentAt = sada;
		lastError = null;
	}

	// Razmak raste puta pet: 1, 5, 25 i 125 minuta. Kratak prekid SMTP-a prođe za minutu, a dug
	// ne pravi pokušaj svakih pola minute. Poslije petog neuspjeha (oko 2,5 sata) se odustaje.
	void neuspjela(Instant sada, String greska) {
		attempts++;
		lastError = greska.length() > NAJDUZA_GRESKA ? greska.substring(0, NAJDUZA_GRESKA) : greska;
		if (attempts >= NAJVISE_POKUSAJA) {
			status = NotificationStatus.FAILED;
		} else {
			nextAttemptAt = sada.plus(Duration.ofMinutes((long) Math.pow(5, attempts - 1)));
		}
	}

	UUID getId() {
		return id;
	}

	User getUser() {
		return user;
	}

	String getSubject() {
		return subject;
	}

	String getBody() {
		return body;
	}

	NotificationStatus getStatus() {
		return status;
	}

	int getAttempts() {
		return attempts;
	}

	Instant getNextAttemptAt() {
		return nextAttemptAt;
	}

	Instant getSentAt() {
		return sentAt;
	}

	String getLastError() {
		return lastError;
	}

}
