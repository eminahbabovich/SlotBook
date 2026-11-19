package ba.slotbook.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

	// Kad su nastali zadnji mailovi jedne vrste za jednog primaoca, najnoviji prvi (granica mailova na sat)
	@Query("""
			SELECT n.createdAt FROM Notification n
			WHERE n.user.id = :userId AND n.type = :type AND n.createdAt > :od
			ORDER BY n.createdAt DESC""")
	List<Instant> createdForUserSince(UUID userId, NotificationType type, Instant od, Pageable koliko);

	@Query("""
			SELECT n.createdAt FROM Notification n
			WHERE n.email = :email AND n.type = :type AND n.createdAt > :od
			ORDER BY n.createdAt DESC""")
	List<Instant> createdForEmailSince(String email, NotificationType type, Instant od, Pageable koliko);

	// Ako je mail upravo u slanju, DELETE čeka zaključan red, a poslije commita sendera red više nije
	// PENDING i ostaje. Zato se ne briše mail koji je u međuvremenu otišao.
	@Modifying
	@Query(nativeQuery = true, value = """
			DELETE FROM notifications WHERE status = 'PENDING' AND invite_id IN (
				SELECT id FROM organization_invites
				WHERE organization_id = :organizationId AND email = :email AND accepted_at IS NULL)""")
	int deletePendingForOpenInvite(UUID organizationId, String email);

}
