package ba.slotbook.booking;

import java.time.Instant;
import java.util.UUID;

import ba.slotbook.resource.Resource;
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
@Table(name = "bookings")
public class Booking {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "resource_id", updatable = false)
	private Resource resource;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "client_id", updatable = false)
	private User client;

	// Termin se ne pomjera. Drugo vrijeme je nova rezervacija, da historija ostane tačna.
	@Column(name = "start_time", nullable = false, updatable = false)
	private Instant startTime;

	@Column(name = "end_time", nullable = false, updatable = false)
	private Instant endTime;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private BookingStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "idempotency_key", updatable = false)
	private String idempotencyKey;

	protected Booking() {
	}

	Booking(Resource resource, User client, Instant startTime, Instant endTime, BookingStatus status,
			Instant createdAt, String idempotencyKey) {
		this.resource = resource;
		this.client = client;
		this.startTime = startTime;
		this.endTime = endTime;
		this.status = status;
		this.createdAt = createdAt;
		this.idempotencyKey = idempotencyKey;
	}

	public UUID getId() {
		return id;
	}

	public Resource getResource() {
		return resource;
	}

	public User getClient() {
		return client;
	}

	public Instant getStartTime() {
		return startTime;
	}

	public Instant getEndTime() {
		return endTime;
	}

	public BookingStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
