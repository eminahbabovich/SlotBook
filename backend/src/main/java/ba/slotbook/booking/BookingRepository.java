package ba.slotbook.booking;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface BookingRepository extends JpaRepository<Booking, UUID> {

	// Rezervacije koje se preklapaju s periodom od..do: počinju prije kraja i završavaju poslije početka
	List<Booking> findByResourceIdAndStatusInAndStartTimeBeforeAndEndTimeAfter(UUID resourceId,
			Collection<BookingStatus> statusi, Instant to, Instant from);

	// Pokriva ga indeks iza UNIQUE (client_id, idempotency_key)
	Optional<Booking> findByClientIdAndIdempotencyKey(UUID clientId, String idempotencyKey);

}
