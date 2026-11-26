package ba.slotbook.organization;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "organizations")
public class Organization {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false, unique = true, updatable = false)
	private String slug;

	@Column(nullable = false)
	private String timezone;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "no_show_limit")
	private Integer noShowLimit;

	@Column(name = "no_show_window_days")
	private Integer noShowWindowDays;

	@Column(name = "no_show_block_days")
	private Integer noShowBlockDays;

	protected Organization() {
	}

	Organization(String name, String slug, String timezone, Instant createdAt) {
		this.name = name.strip();
		this.slug = slug;
		this.timezone = timezone;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getSlug() {
		return slug;
	}

	public String getTimezone() {
		return timezone;
	}

	public NoShowRules getNedolasci() {
		return new NoShowRules(noShowLimit, noShowWindowDays, noShowBlockDays);
	}

	void promijeniNedolaske(NoShowRules pravila) {
		this.noShowLimit = pravila.limit();
		this.noShowWindowDays = pravila.windowDays();
		this.noShowBlockDays = pravila.blockDays();
	}

}
