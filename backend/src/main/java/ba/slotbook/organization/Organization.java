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

}
