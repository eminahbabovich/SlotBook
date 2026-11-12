package ba.slotbook.organization;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface OrganizationRepository extends JpaRepository<Organization, UUID> {

	Optional<Organization> findBySlug(String slug);

}
