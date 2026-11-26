package ba.slotbook.organization;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

interface OrganizationRepository extends JpaRepository<Organization, UUID> {

	Optional<Organization> findBySlug(String slug);

	// Red organizacije kao brava za promjene članova: dva admina koja u isto vrijeme spuštaju jedan
	// drugog idu jedan za drugim, pa drugi vidi da je ostao zadnji admin
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from Organization o where o.id = :id")
	Optional<Organization> findLockedById(UUID id);

}
