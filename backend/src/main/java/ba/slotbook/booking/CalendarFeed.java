package ba.slotbook.booking;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Samo za čitanje. Upis ide kroz CalendarFeedRepository.replace (jedna SQL naredba), da dupli klik
// na "Napravi link" ne padne na primarnom ključu.
@Entity
@Table(name = "calendar_feeds")
public class CalendarFeed {

	@Id
	@Column(name = "user_id")
	private UUID userId;

	@Column(name = "token_hash", nullable = false)
	private String tokenHash;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected CalendarFeed() {
	}

	public UUID getUserId() {
		return userId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
