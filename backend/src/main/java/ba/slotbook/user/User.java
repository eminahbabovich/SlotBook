package ba.slotbook.user;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "display_name", nullable = false)
	private String displayName;

	@Column(name = "email_verified_at")
	private Instant emailVerifiedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	// Hibernate pravi objekat ovim konstruktorom kad čita red iz baze, a nama ne treba
	protected User() {
	}

	public User(String email, String passwordHash, String displayName, Instant createdAt) {
		this.email = normalizeEmail(email);
		this.passwordHash = passwordHash;
		this.displayName = displayName.strip();
		this.createdAt = createdAt;
	}

	// Locale.ROOT, jer bi na turskom locale-u "I" postalo "ı" i email se ne bi našao
	public static String normalizeEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void changePassword(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	// Prva potvrda ostaje: drugi klik na drugi link iz istog dana ne mijenja vrijeme
	public void verifyEmail(Instant sada) {
		if (emailVerifiedAt == null) {
			emailVerifiedAt = sada;
		}
	}

	public boolean isEmailVerified() {
		return emailVerifiedAt != null;
	}

}
