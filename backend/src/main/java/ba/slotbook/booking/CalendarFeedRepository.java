package ba.slotbook.booking;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface CalendarFeedRepository extends JpaRepository<CalendarFeed, UUID> {

	Optional<CalendarFeed> findByTokenHash(String tokenHash);

	// Upsert: novi link zamijeni stari u istoj naredbi. Dva istovremena zahtjeva ne padnu na
	// primarnom ključu, nego drugi pobijedi, a važi samo njegov token.
	@Modifying
	@Query(nativeQuery = true, value = """
			insert into calendar_feeds (user_id, token_hash, created_at) values (:userId, :tokenHash, :sada)
			on conflict (user_id) do update set token_hash = excluded.token_hash, created_at = excluded.created_at""")
	void replace(UUID userId, String tokenHash, Instant sada);

	@Modifying
	@Query(nativeQuery = true, value = "delete from calendar_feeds where user_id = :userId")
	void deleteForUser(UUID userId);

}
