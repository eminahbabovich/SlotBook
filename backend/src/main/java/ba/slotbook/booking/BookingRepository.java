package ba.slotbook.booking;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface BookingRepository extends JpaRepository<Booking, UUID> {

	// Rezervacije koje se preklapaju s periodom od..do: počinju prije kraja i završavaju poslije početka
	List<Booking> findByResourceIdAndStatusInAndStartTimeBeforeAndEndTimeAfter(UUID resourceId,
			Collection<BookingStatus> statusi, Instant to, Instant from);

	// join fetch: naziv resursa i zona organizacije stižu u istom upitu, a ne jedan upit po redu (N+1)
	@Query("select b from Booking b join fetch b.resource r join fetch r.organization where b.client.id = :clientId")
	Page<Booking> findMine(UUID clientId, Pageable pageable);

	// Jedna naredba mijenja status i piše historiju, pa ne može biti jedno bez drugog. Kad provider
	// potvrđuje u istom trenutku, UPDATE čeka njegov red i ponovo provjeri status = 'PENDING', pa
	// potvrđena ostane potvrđena. version + 1 je tu za obrnut slučaj: providerov stari @Version tada padne.
	// Bez @Modifying, jer naredba vraća redove: ID-jeve isteklih, da klijenti dobiju obavijest
	@Query(nativeQuery = true, value = """
			WITH istekle AS (
				UPDATE bookings SET status = 'EXPIRED', version = version + 1
				WHERE status = 'PENDING' AND expires_at <= :sada
				RETURNING id),
			historija AS (
				INSERT INTO booking_events (id, booking_id, from_status, to_status, actor_id, created_at)
				SELECT gen_random_uuid(), id, 'PENDING', 'EXPIRED', NULL, :sada FROM istekle)
			SELECT id FROM istekle""")
	List<UUID> expireAll(Instant sada);

	// Isto, samo za jedan resurs, pri novoj rezervaciji pod lockom resursa
	@Query(nativeQuery = true, value = """
			WITH istekle AS (
				UPDATE bookings SET status = 'EXPIRED', version = version + 1
				WHERE status = 'PENDING' AND expires_at <= :sada AND resource_id = :resourceId
				RETURNING id),
			historija AS (
				INSERT INTO booking_events (id, booking_id, from_status, to_status, actor_id, created_at)
				SELECT gen_random_uuid(), id, 'PENDING', 'EXPIRED', NULL, :sada FROM istekle)
			SELECT id FROM istekle""")
	List<UUID> expireOnResource(UUID resourceId, Instant sada);

	// Potvrđene koje počinju u sljedećih N sati i nemaju podsjetnik. Ko je rezervisao manje od N sati
	// unaprijed, ne dobija ga: potvrdu je dobio maloprije, pa bi drugi mail za isti termin bio višak.
	@Query(nativeQuery = true, value = """
			SELECT b.* FROM bookings b
			WHERE b.status = 'CONFIRMED'
				AND b.start_time > :sada
				AND b.start_time <= :granica
				AND b.created_at < b.start_time - make_interval(hours => :sati)
				AND NOT EXISTS (SELECT 1 FROM notifications n
					WHERE n.booking_id = b.id AND n.type = 'BOOKING_REMINDER')""")
	List<Booking> findDueForReminder(Instant sada, Instant granica, int sati);

	@Modifying
	@Query(nativeQuery = true, value = """
			WITH zavrsene AS (
				UPDATE bookings SET status = 'COMPLETED', version = version + 1
				WHERE status = 'CONFIRMED' AND end_time <= :granica
				RETURNING id)
			INSERT INTO booking_events (id, booking_id, from_status, to_status, actor_id, created_at)
			SELECT gen_random_uuid(), id, 'CONFIRMED', 'COMPLETED', NULL, :sada FROM zavrsene""")
	int completeEndedBefore(Instant granica, Instant sada);

	// Pokriva ga indeks iza UNIQUE (client_id, idempotency_key)
	Optional<Booking> findByClientIdAndIdempotencyKey(UUID clientId, String idempotencyKey);

}
