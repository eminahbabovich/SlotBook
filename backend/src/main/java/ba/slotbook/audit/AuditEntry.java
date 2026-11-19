package ba.slotbook.audit;

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
@Table(name = "audit_log")
public class AuditEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "organization_id", updatable = false)
	private UUID organizationId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "actor_id", updatable = false)
	private User actor;

	@Enumerated(EnumType.STRING)
	@Column(updatable = false)
	private AuditAction action;

	@Column(name = "target_id", updatable = false)
	private UUID targetId;

	@Column(updatable = false)
	private String detail;

	@Column(name = "created_at", updatable = false)
	private Instant createdAt;

	protected AuditEntry() {
	}

	AuditEntry(UUID organizationId, User actor, AuditAction action, UUID targetId, String detail, Instant createdAt) {
		this.organizationId = organizationId;
		this.actor = actor;
		this.action = action;
		this.targetId = targetId;
		this.detail = detail;
		this.createdAt = createdAt;
	}

	User getActor() {
		return actor;
	}

	AuditAction getAction() {
		return action;
	}

	UUID getTargetId() {
		return targetId;
	}

	String getDetail() {
		return detail;
	}

	Instant getCreatedAt() {
		return createdAt;
	}

}
