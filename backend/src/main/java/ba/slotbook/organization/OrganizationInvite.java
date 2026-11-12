package ba.slotbook.organization;

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
@Table(name = "organization_invites")
public class OrganizationInvite {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "organization_id", updatable = false)
	private Organization organization;

	@Column(nullable = false, updatable = false)
	private String email;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private OrganizationRole role;

	@Column(name = "token_hash", nullable = false, unique = true, updatable = false)
	private String tokenHash;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "accepted_at")
	private Instant acceptedAt;

	protected OrganizationInvite() {
	}

	OrganizationInvite(Organization organization, String email, OrganizationRole role, String tokenHash,
			Instant createdAt, Instant expiresAt) {
		this.organization = organization;
		this.email = User.normalizeEmail(email);
		this.role = role;
		this.tokenHash = tokenHash;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public OrganizationRole getRole() {
		return role;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Organization getOrganization() {
		return organization;
	}

	// Ista granica kao kod refresh tokena: u trenutku isteka pozivnica više ne važi
	boolean isExpired(Instant sada) {
		return !sada.isBefore(expiresAt);
	}

}
