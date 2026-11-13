package ba.slotbook.organization;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, OrganizationMember.Id> {

	// join fetch: korisnici dolaze u istom upitu. Bez toga je user LAZY, pa bi DTO za svakog
	// člana okinuo još jedan SELECT (N+1). COUNT za Page Spring Data izvede iz ovog upita sam.
	@Query("""
			select m from OrganizationMember m
			join fetch m.user
			where m.id.organizationId = :organizationId""")
	Page<OrganizationMember> findAllWithUser(UUID organizationId, Pageable pageable);

	boolean existsByIdOrganizationIdAndUserEmail(UUID organizationId, String email);

}
