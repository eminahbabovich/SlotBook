package ba.slotbook.booking;

import java.time.Instant;
import java.util.UUID;

import ba.slotbook.availability.TimeRange;
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

// Ponudu i istek mijenja posao SQL naredbama (WaitlistEntryRepository), a entitet samo otkazivanje
@Entity
@Table(name = "waitlist_entries")
public class WaitlistEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "resource_id", updatable = false)
	private Resource resource;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "client_id", updatable = false)
	private User client;

	@Column(name = "start_time", nullable = false, updatable = false)
	private Instant startTime;

	@Column(name = "end_time", nullable = false, updatable = false)
	private Instant endTime;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private WaitlistStatus status;

	@Column(name = "offered_until")
	private Instant offeredUntil;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected WaitlistEntry() {
	}

	WaitlistEntry(Resource resource, User client, TimeRange termin, Instant createdAt) {
		this.resource = resource;
		this.client = client;
		this.startTime = termin.start();
		this.endTime = termin.end();
		this.status = WaitlistStatus.WAITING;
		this.createdAt = createdAt;
	}

	// Završen upis (rezervisan, istekao) se ne dira, da ispisivanje ne promijeni historiju
	void otkazi() {
		if (WaitlistStatus.ZIVI.contains(status)) {
			status = WaitlistStatus.CANCELLED;
		}
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

	public WaitlistStatus getStatus() {
		return status;
	}

	public Instant getOfferedUntil() {
		return offeredUntil;
	}

}
