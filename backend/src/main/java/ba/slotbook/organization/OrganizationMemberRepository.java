package ba.slotbook.organization;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, OrganizationMember.Id> {

	// join fetch: korisnici dolaze u istom upitu. Bez toga je user LAZY, pa bi DTO za svakog
	// člana okinuo još jedan SELECT (N+1). COUNT za Page Spring Data izvede iz ovog upita sam.
	@Query("""
			select m from OrganizationMember m
			join fetch m.user
			where m.id.organizationId = :organizationId""")
	Page<OrganizationMember> findAllWithUser(UUID organizationId, Pageable pageable);

	// join fetch: organizacija je LAZY, a DTO čita njen naziv, slug i zonu za svako članstvo.
	// Uslov je samo korisnik iz tokena, pa tuđa članstva ne mogu ući u listu.
	@Query("""
			select m from OrganizationMember m
			join fetch m.organization o
			where m.id.userId = :userId
			order by o.name, o.id""")
	List<OrganizationMember> findAllByUserWithOrganization(UUID userId);

	boolean existsByIdOrganizationIdAndUserEmail(UUID organizationId, String email);

	// 1 kad je osoba upisana, 0 kad je već bila član (bilo koje uloge). ON CONFLICT umjesto provjere
	// pa upisa: dva istovremena ulaska iste osobe ne padaju na primarnom ključu, nego drugi dobije 0.
	@Modifying
	@Query(nativeQuery = true, value = """
			insert into organization_members (organization_id, user_id, role, joined_at)
			values (:organizationId, :userId, 'CLIENT', :sada)
			on conflict do nothing""")
	int dodajKlijenta(UUID organizationId, UUID userId, Instant sada);

	long countByIdOrganizationIdAndRole(UUID organizationId, OrganizationRole role);

	// Native: resursi su u drugom paketu, a ovdje treba samo ime. Svi, i deaktivirani, jer i deaktiviran
	// resurs ima strani ključ na članstvo providera.
	@Query(nativeQuery = true, value = """
			select name from resources where organization_id = :organizationId and provider_id = :userId order by name""")
	List<String> resursiKojeVodi(UUID organizationId, UUID userId);

}
