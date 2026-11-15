package ba.slotbook.resource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface ResourceRepository extends JpaRepository<Resource, UUID> {

	// Uz Pageable Spring Data pošalje dva upita: jednu stranicu (LIMIT/OFFSET) i COUNT za ukupan broj
	Page<Resource> findByOrganizationId(UUID organizationId, Pageable pageable);

	Page<Resource> findByOrganizationIdAndActiveTrue(UUID organizationId, Pageable pageable);

	Optional<Resource> findByIdAndOrganizationId(UUID id, UUID organizationId);

	// Za statistiku: svi resursi, i deaktivirani, jer su u prošlom periodu možda radili
	List<Resource> findByOrganizationIdOrderByNameAscIdAsc(UUID organizationId);

	List<Resource> findByOrganizationIdAndProviderIdOrderByNameAscIdAsc(UUID organizationId, UUID providerId);

	// SELECT ... FOR UPDATE: drugi zahtjev koji mijenja isti resurs čeka kraj ove transakcije
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Resource r where r.id = :id")
	Optional<Resource> findLockedById(UUID id);

}
