package ba.slotbook.resource;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceRepository extends JpaRepository<Resource, UUID> {

	// Uz Pageable Spring Data pošalje dva upita: jednu stranicu (LIMIT/OFFSET) i COUNT za ukupan broj
	Page<Resource> findByOrganizationId(UUID organizationId, Pageable pageable);

}
