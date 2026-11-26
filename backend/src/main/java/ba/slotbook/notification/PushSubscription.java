package ba.slotbook.notification;

import java.time.Instant;
import java.util.UUID;

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

// Jedan browser na jednom uređaju, prijavljen za push obavijesti jednog korisnika
@Entity
@Table(name = "push_subscriptions")
public class PushSubscription {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(nullable = false, updatable = false)
	private String endpoint;

	@Column(nullable = false)
	private String p256dh;

	@Column(nullable = false)
	private String auth;

	private String device;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected PushSubscription() {
	}

	PushSubscription(User user, String endpoint, String p256dh, String auth, String device, Instant createdAt) {
		this.user = user;
		this.endpoint = endpoint;
		this.p256dh = p256dh;
		this.auth = auth;
		this.device = device;
		this.createdAt = createdAt;
	}

	// Isti browser se ponovo prijavio, možda pod drugim nalogom: obavijesti od sad idu tom nalogu
	void preuzmi(User noviKorisnik, String noviP256dh, String noviAuth, String noviUredjaj, Instant sada) {
		user = noviKorisnik;
		p256dh = noviP256dh;
		auth = noviAuth;
		device = noviUredjaj;
		createdAt = sada;
	}

	UUID getId() {
		return id;
	}

	String getEndpoint() {
		return endpoint;
	}

	String getP256dh() {
		return p256dh;
	}

	String getAuth() {
		return auth;
	}

	String getDevice() {
		return device;
	}

	Instant getCreatedAt() {
		return createdAt;
	}

}
