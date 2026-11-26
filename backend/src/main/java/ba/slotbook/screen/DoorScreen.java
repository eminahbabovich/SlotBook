package ba.slotbook.screen;

import java.time.Instant;
import java.util.UUID;

import ba.slotbook.organization.Organization;
import ba.slotbook.resource.Resource;
import ba.slotbook.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "door_screens")
public class DoorScreen {

	// ID daje Java, kao kod pozivnog linka: token se računa iz ID-ja, pa ID treba prije upisa
	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "organization_id", updatable = false)
	private Organization organization;

	// null: ekran za cijelu organizaciju
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "resource_id", updatable = false)
	private Resource resource;

	@Column(name = "token_hash", nullable = false, unique = true, updatable = false)
	private String tokenHash;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by", updatable = false)
	private User createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected DoorScreen() {
	}

	DoorScreen(UUID id, Organization organization, Resource resource, String tokenHash, User createdBy,
			Instant createdAt) {
		this.id = id;
		this.organization = organization;
		this.resource = resource;
		this.tokenHash = tokenHash;
		this.createdBy = createdBy;
		this.createdAt = createdAt;
	}

	void ugasi(Instant sada) {
		if (revokedAt == null) {
			revokedAt = sada;
		}
	}

	UUID getId() {
		return id;
	}

	Organization getOrganization() {
		return organization;
	}

	Resource getResource() {
		return resource;
	}

	Instant getRevokedAt() {
		return revokedAt;
	}

	Instant getCreatedAt() {
		return createdAt;
	}

}
