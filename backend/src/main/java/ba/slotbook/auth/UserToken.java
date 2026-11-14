package ba.slotbook.auth;

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
@Table(name = "user_tokens")
class UserToken {

	enum Type {
		EMAIL_VERIFY
	}

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", updatable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private Type type;

	@Column(name = "token_hash", nullable = false, updatable = false)
	private String tokenHash;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	protected UserToken() {
	}

	UserToken(User user, Type type, String tokenHash, Instant createdAt, Instant expiresAt) {
		this.user = user;
		this.type = type;
		this.tokenHash = tokenHash;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	// Tačno na roku token više ne važi, isto kao kod pozivnica
	boolean isExpired(Instant sada) {
		return !sada.isBefore(expiresAt);
	}

	UUID getId() {
		return id;
	}

	User getUser() {
		return user;
	}

}
