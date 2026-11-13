package ba.slotbook.availability;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface AvailabilityRuleRepository extends JpaRepository<AvailabilityRule, UUID> {

	List<AvailabilityRule> findByResourceId(UUID resourceId);

	// JPQL delete ide u bazu odmah. Spring Data deleteBy... bi brisao pri flushu, poslije INSERT-a
	// novih pravila, i novo pravilo bi se "preklopilo" sa starim koje još nije obrisano (Faza 2, pozivnice).
	@Modifying
	@Query("delete from AvailabilityRule r where r.resource.id = :resourceId")
	void deleteAllOfResource(UUID resourceId);

}
