package ba.slotbook.booking;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface BookingRepository extends JpaRepository<Booking, UUID> {

	// Rezervacije koje se preklapaju s periodom od..do: počinju prije kraja i završavaju poslije početka
	List<Booking> findByResourceIdAndStatusInAndStartTimeBeforeAndEndTimeAfter(UUID resourceId,
			Collection<BookingStatus> statusi, Instant to, Instant from);

	// join fetch: naziv resursa i zona organizacije stižu u istom upitu, a ne jedan upit po redu (N+1)
	@Query("select b from Booking b join fetch b.resource r join fetch r.organization where b.client.id = :clientId")
	Page<Booking> findMine(UUID clientId, Pageable pageable);

	// Pokriva ga indeks iza UNIQUE (client_id, idempotency_key)
	Optional<Booking> findByClientIdAndIdempotencyKey(UUID clientId, String idempotencyKey);

}
