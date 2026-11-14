package ba.slotbook.notification;

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

	protected Notification() {
	}

	Notification(User user, NotificationType type, UUID bookingId, Instant createdAt) {
		this.user = user;
		this.type = type;
		this.bookingId = bookingId;
		this.status = NotificationStatus.PENDING;
		this.nextAttemptAt = createdAt;
		this.createdAt = createdAt;
	}

}
