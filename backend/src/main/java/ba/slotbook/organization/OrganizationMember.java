package ba.slotbook.organization;

import java.time.Instant;
import java.util.UUID;

import ba.slotbook.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "organization_members")
public class OrganizationMember {

	// Ključ od dvije kolone. Record daje equals i hashCode, a Hibernate po njima poredi ključeve.
	@Embeddable
	public record Id(
			@Column(name = "organization_id") UUID organizationId,
			@Column(name = "user_id") UUID userId) {
	}

	@EmbeddedId
	private Id id;

	// LAZY: ko učita članstvo, ne učitava odmah i organizaciju i korisnika. ManyToOne je inače
	// EAGER, pa bi lista od 50 članova povukla još do 100 upita.
	@MapsId("organizationId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "organization_id")
	private Organization organization;

	@MapsId("userId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private User user;

	// STRING, ne ORDINAL: u bazi piše "ADMIN", a ne 0, pa novi enum ispred postojećih ne mijenja značenje redova
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OrganizationRole role;

	@Column(name = "joined_at", nullable = false, updatable = false)
	private Instant joinedAt;

	protected OrganizationMember() {
	}

	OrganizationMember(Organization organization, User user, OrganizationRole role, Instant joinedAt) {
		this.id = new Id(organization.getId(), user.getId());
		this.organization = organization;
		this.user = user;
		this.role = role;
		this.joinedAt = joinedAt;
	}

	public OrganizationRole getRole() {
		return role;
	}

}
