package ba.slotbook.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	// Samo ID, ne @ManyToOne User: ovdje nam od korisnika ne treba ništa osim ID-ja za novi access token
	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "family_id", nullable = false, updatable = false)
	private UUID familyId;

	@Column(name = "token_hash", nullable = false, updatable = false)
	private String tokenHash;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "replaced_by")
	private UUID replacedBy;

	protected RefreshToken() {
	}

	RefreshToken(UUID userId, UUID familyId, String tokenHash, Instant createdAt, Instant expiresAt) {
		this.userId = userId;
		this.familyId = familyId;
		this.tokenHash = tokenHash;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	boolean isRevoked() {
		return revokedAt != null;
	}

	boolean isExpired(Instant sada) {
		return !expiresAt.isAfter(sada);
	}

	void replaceWith(UUID noviToken, Instant sada) {
		this.revokedAt = sada;
		this.replacedBy = noviToken;
	}

	UUID getId() {
		return id;
	}

	UUID getUserId() {
		return userId;
	}

	UUID getFamilyId() {
		return familyId;
	}

}
