package ba.slotbook.organization;

import java.time.Instant;
import java.util.UUID;

import ba.slotbook.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "invite_links")
public class InviteLink {

	// ID daje Java, a ne baza: token se računa iz ID-ja, pa ID treba prije upisa
	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "organization_id", updatable = false)
	private Organization organization;

	@Column(name = "token_hash", nullable = false, unique = true, updatable = false)
	private String tokenHash;

	@Column(name = "max_uses", nullable = false, updatable = false)
	private int maxUses;

	// Mijenja ga samo InviteLinkRepository.iskoristi, jednim UPDATE-om u bazi
	@Column(nullable = false, insertable = false, updatable = false)
	private int uses;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by", updatable = false)
	private User createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected InviteLink() {
	}

	InviteLink(UUID id, Organization organization, String tokenHash, int maxUses, Instant expiresAt, User createdBy,
			Instant createdAt) {
		this.id = id;
		this.organization = organization;
		this.tokenHash = tokenHash;
		this.maxUses = maxUses;
		this.expiresAt = expiresAt;
		this.createdBy = createdBy;
		this.createdAt = createdAt;
	}

	// Povučen ostaje povučen, pa drugi DELETE ne mijenja vrijeme prvog
	void povuci(Instant sada) {
		if (revokedAt == null) {
			revokedAt = sada;
		}
	}

	boolean vazi(Instant sada) {
		return revokedAt == null && sada.isBefore(expiresAt) && uses < maxUses;
	}

	public UUID getId() {
		return id;
	}

	public Organization getOrganization() {
		return organization;
	}

	public int getMaxUses() {
		return maxUses;
	}

	public int getUses() {
		return uses;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
