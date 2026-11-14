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

	// Bez automatske potvrde rezervacija čeka providera u stanju PENDING
	@Column(name = "auto_confirm", nullable = false)
	private boolean autoConfirm;

	@Column(name = "cancel_notice_hours", nullable = false)
	private int cancelNoticeHours;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "image_key")
	private String imageKey;

	protected Resource() {
	}

	Resource(Organization organization, User provider, String name, String description, int slotMinutes,
			boolean autoConfirm, int cancelNoticeHours, Instant createdAt) {
		this.organization = organization;
		this.provider = provider;
		this.active = true;
		this.createdAt = createdAt;
		update(name, description, slotMinutes, autoConfirm, cancelNoticeHours);
	}

	void update(String name, String description, int slotMinutes, boolean autoConfirm, int cancelNoticeHours) {
		this.name = name.strip();
		// Prazan opis iz forme je isto što i bez opisa, da frontend ne mora razlikovati "" i null
		this.description = description == null || description.isBlank() ? null : description.strip();
		this.slotMinutes = slotMinutes;
		this.autoConfirm = autoConfirm;
		this.cancelNoticeHours = cancelNoticeHours;
	}

	void changeProvider(User provider) {
		this.provider = provider;
	}

	// Red ostaje jer će na njega pokazivati rezervacije, a klijent ga više ne vidi u listi
	void deactivate() {
		this.active = false;
	}

	void changeImage(String imageKey) {
		this.imageKey = imageKey;
	}

	public String getImageKey() {
		return imageKey;
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

	public boolean isAutoConfirm() {
		return autoConfirm;
	}

	public int getCancelNoticeHours() {
		return cancelNoticeHours;
	}

}
