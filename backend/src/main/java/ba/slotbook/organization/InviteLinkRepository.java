package ba.slotbook.organization;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface InviteLinkRepository extends JpaRepository<InviteLink, UUID> {

	@Query("select l from InviteLink l join fetch l.organization where l.tokenHash = :tokenHash")
	Optional<InviteLink> findByTokenHash(String tokenHash);

	Page<InviteLink> findByOrganizationId(UUID organizationId, Pageable pageable);

	Optional<InviteLink> findByIdAndOrganizationId(UUID id, UUID organizationId);

	// Zadnji link koji još radi, za QR na ekranu na vratima
	Optional<InviteLink> findFirstByOrganizationIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
			UUID organizationId, Instant sada);

	// Brojanje i provjera u jednom UPDATE-u. Od 20 istovremenih ulazaka na link s 5 mjesta Postgres
	// ih pusti jedan po jedan kroz zaključan red, a svaki sljedeći vidi uses koji je prethodni upisao.
	// Šesti dobije 0 redova. Provjera u Javi (pročitaj uses, pa upiši uses + 1) bi pustila sve koji
	// pročitaju isto, pa bi ih ušlo više od pet.
	@Modifying
	@Query(nativeQuery = true, value = """
			update invite_links set uses = uses + 1
			where id = :id and revoked_at is null and expires_at > :sada and uses < max_uses""")
	int iskoristi(UUID id, Instant sada);

}
