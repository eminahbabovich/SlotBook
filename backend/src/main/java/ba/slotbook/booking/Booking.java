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
import jakarta.persistence.Version;

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

	// Provider potvrđuje dok klijent otkazuje: bez ovoga oba pročitaju PENDING, oba prođu i
	// pobijedi ko zadnji upiše, a historija ima dva prelaza iz PENDING. Sa @Version drugi padne.
	@Version
	private long version;

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

	// Pravilo prelaza je u entitetu, pa ga ne može zaobići nijedan servis koji mijenja status
	void prijedi(BookingStatus novi, Instant sada) {
		if (!status.mozePreciU(novi)) {
			throw new BookingTransitionException(status, novi);
		}
		if (novi == BookingStatus.NO_SHOW && sada.isBefore(startTime)) {
			throw new NoShowTooEarlyException();
		}
		this.status = novi;
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
