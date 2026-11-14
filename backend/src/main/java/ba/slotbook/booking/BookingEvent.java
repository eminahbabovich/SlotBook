package ba.slotbook.booking;

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

// Zapis se nikad ne mijenja, zato su sve kolone updatable = false i nema metoda za izmjenu
@Entity
@Table(name = "booking_events")
public class BookingEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_id", updatable = false)
	private Booking booking;

	@Enumerated(EnumType.STRING)
	@Column(name = "from_status", updatable = false)
	private BookingStatus fromStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "to_status", nullable = false, updatable = false)
	private BookingStatus toStatus;

	// Prazno kad je status promijenio pozadinski posao (istek, zatvaranje završene)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "actor_id", updatable = false)
	private User actor;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected BookingEvent() {
	}

	BookingEvent(Booking booking, BookingStatus fromStatus, BookingStatus toStatus, User actor, Instant createdAt) {
		this.booking = booking;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.actor = actor;
		this.createdAt = createdAt;
	}

	public BookingStatus getFromStatus() {
		return fromStatus;
	}

	public BookingStatus getToStatus() {
		return toStatus;
	}

	public User getActor() {
		return actor;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
