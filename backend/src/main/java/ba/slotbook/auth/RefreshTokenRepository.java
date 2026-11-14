package ba.slotbook.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	// SELECT ... FOR UPDATE: drugi zahtjev s istim tokenom čeka dok prvi ne završi transakciju
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<RefreshToken> findByTokenHash(String tokenHash);

	@Modifying
	@Query("update RefreshToken t set t.revokedAt = :sada where t.familyId = :familyId and t.revokedAt is null")
	int revokeFamily(UUID familyId, Instant sada);

	@Modifying
	@Query("update RefreshToken t set t.revokedAt = :sada where t.userId = :userId and t.revokedAt is null")
	int revokeAllOf(UUID userId, Instant sada);

}
