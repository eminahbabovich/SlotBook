package ba.slotbook.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

	Optional<PushSubscription> findByEndpoint(String endpoint);

	Optional<PushSubscription> findByEndpointAndUserId(String endpoint, UUID userId);

	List<PushSubscription> findByUserId(UUID userId);

	// Upit, a ne delete(entitet): baza uz pretplatu kaskadno briše i njene obavijesti, među njima i onu
	// koja se upravo šalje, a Hibernate za to ne zna i ne pokušava je poslije mijenjati.
	@Modifying
	@Transactional
	@Query("DELETE FROM PushSubscription s WHERE s.id = :id")
	int ukloni(UUID id);

}
