package ba.slotbook.notification;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface NotificationRepository extends JpaRepository<Notification, UUID> {

	// FOR UPDATE drži red dok traje slanje. SKIP LOCKED: drugi krug posla ili druga instanca preskoči
	// red koji se upravo šalje i uzme sljedeći, umjesto da čeka pa isti mail pošalje još jednom.
	@Query(nativeQuery = true, value = """
			SELECT * FROM notifications
			WHERE status = 'PENDING' AND next_attempt_at <= :sada
			ORDER BY next_attempt_at
			LIMIT 1
			FOR UPDATE SKIP LOCKED""")
	Optional<Notification> findNextDue(Instant sada);

}
