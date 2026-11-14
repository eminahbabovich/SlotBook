package ba.slotbook.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface UserTokenRepository extends JpaRepository<UserToken, UUID> {

	Optional<UserToken> findByTokenHashAndType(String tokenHash, UserToken.Type type);

	// Dva klika u istom trenutku: prvi upiše used_at, drugi čeka zaključan red i onda ne nađe ništa (0)
	@Modifying
	@Query("update UserToken t set t.usedAt = :sada where t.id = :id and t.usedAt is null")
	int markUsed(UUID id, Instant sada);

}
