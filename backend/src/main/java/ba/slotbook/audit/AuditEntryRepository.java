package ba.slotbook.audit;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

	// left join fetch: ime izvršioca u istom upitu (bez N+1), a left jer obrisan nalog ostavi actor null
	@Query("""
			select a from AuditEntry a
			left join fetch a.actor
			where a.organizationId = :organizationId""")
	Page<AuditEntry> findForOrganization(UUID organizationId, Pageable pageable);

}
