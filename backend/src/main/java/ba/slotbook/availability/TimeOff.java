package ba.slotbook.availability;

import java.time.Instant;
import java.util.UUID;

import ba.slotbook.resource.Resource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "time_off")
public class TimeOff {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "resource_id", updatable = false)
	private Resource resource;

	@Column(name = "starts_at", nullable = false)
	private Instant startsAt;

	@Column(name = "ends_at", nullable = false)
	private Instant endsAt;

	private String reason;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected TimeOff() {
	}

	TimeOff(Resource resource, Instant startsAt, Instant endsAt, String reason, Instant createdAt) {
		this.resource = resource;
		this.startsAt = startsAt;
		this.endsAt = endsAt;
		this.reason = reason == null || reason.isBlank() ? null : reason.strip();
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public Instant getStartsAt() {
		return startsAt;
	}

	public Instant getEndsAt() {
		return endsAt;
	}

	public String getReason() {
		return reason;
	}

}
