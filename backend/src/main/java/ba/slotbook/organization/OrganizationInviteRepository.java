package ba.slotbook.organization;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface OrganizationInviteRepository extends JpaRepository<OrganizationInvite, UUID> {

	// Bulk DELETE ide u bazu odmah. delete(entitet) bi čekao flush, a Hibernate pri flushu radi
	// INSERT prije DELETE-a, pa bi nova pozivnica pala na indeksu organization_invites_jedna_otvorena.
	@Modifying
	@Query("""
			delete from OrganizationInvite i
			where i.organization.id = :organizationId and i.email = :email and i.acceptedAt is null""")
	int deleteOpen(UUID organizationId, String email);

	// Otvorene su i istekle: admin vidi da je istekla i može poslati novu
	Page<OrganizationInvite> findByOrganizationIdAndAcceptedAtIsNull(UUID organizationId, Pageable pageable);

	Optional<OrganizationInvite> findByIdAndOrganizationIdAndAcceptedAtIsNull(UUID id, UUID organizationId);

	@Query("select i from OrganizationInvite i join fetch i.organization where i.tokenHash = :tokenHash")
	Optional<OrganizationInvite> findByTokenHash(String tokenHash);

	// Uslov u WHERE, a ne provjera u Javi: od dva istovremena prihvatanja Postgres pusti jedno,
	// a drugo čeka zaključan red i onda vidi da accepted_at više nije prazan (0 redova)
	@Modifying
	@Query("update OrganizationInvite i set i.acceptedAt = :sada where i.id = :id and i.acceptedAt is null")
	int markAccepted(UUID id, Instant sada);

}
