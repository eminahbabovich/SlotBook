package ba.slotbook.screen;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import ba.slotbook.organization.Organization;
import jakarta.persistence.LockModeType;

interface DoorScreenRepository extends JpaRepository<DoorScreen, UUID> {

	// Odgovor nosi naziv resursa, pa se resurs učita u istom upitu
	@Query("""
			select s from DoorScreen s left join fetch s.resource
			where s.organization.id = :organizationId and s.revokedAt is null
			order by s.createdAt desc, s.id""")
	List<DoorScreen> findLive(UUID organizationId);

	@Query("select s from DoorScreen s join fetch s.organization left join fetch s.resource where s.tokenHash = :tokenHash")
	Optional<DoorScreen> findByTokenHash(String tokenHash);

	Optional<DoorScreen> findByIdAndOrganizationId(UUID id, UUID organizationId);

	Optional<DoorScreen> findByOrganizationIdAndResourceIsNullAndRevokedAtIsNull(UUID organizationId);

	Optional<DoorScreen> findByOrganizationIdAndResourceIdAndRevokedAtIsNull(UUID organizationId, UUID resourceId);

	// Dva admina koja u isto vrijeme prave ekran za isto mjesto idu jedan za drugim. Bez toga bi oba
	// vidjela "nema živog", a drugi bi pao na indeks door_screens_jedan_zivi sa 500.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from Organization o where o.id = :id")
	Optional<Organization> lockOrganization(UUID id);

}
