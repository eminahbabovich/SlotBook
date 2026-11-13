package ba.slotbook.resource;

import java.time.Instant;
import java.util.UUID;

import ba.slotbook.organization.Organization;
import ba.slotbook.user.User;
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
@Table(name = "resources")
public class Resource {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	// Resurs ne prelazi u drugu organizaciju, pa se veza ne mijenja
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "organization_id", updatable = false)
	private Organization organization;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "provider_id")
	private User provider;

	@Column(nullable = false)
	private String name;

	private String description;

	@Column(name = "slot_minutes", nullable = false)
	private int slotMinutes;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Resource() {
	}

	Resource(Organization organization, User provider, String name, String description, int slotMinutes,
			Instant createdAt) {
		this.organization = organization;
		this.provider = provider;
		this.active = true;
		this.createdAt = createdAt;
		update(name, description, slotMinutes);
	}

	void update(String name, String description, int slotMinutes) {
		this.name = name.strip();
		// Prazan opis iz forme je isto što i bez opisa, da frontend ne mora razlikovati "" i null
		this.description = description == null || description.isBlank() ? null : description.strip();
		this.slotMinutes = slotMinutes;
	}

	void changeProvider(User provider) {
		this.provider = provider;
	}

	// Red ostaje jer će na njega pokazivati rezervacije, a klijent ga više ne vidi u listi
	void deactivate() {
		this.active = false;
	}

	public UUID getId() {
		return id;
	}

	public Organization getOrganization() {
		return organization;
	}

	public User getProvider() {
		return provider;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public int getSlotMinutes() {
		return slotMinutes;
	}

	public boolean isActive() {
		return active;
	}

}
