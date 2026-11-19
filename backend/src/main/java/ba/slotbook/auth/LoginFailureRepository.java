package ba.slotbook.auth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

interface LoginFailureRepository extends JpaRepository<LoginFailure, String> {

	// Dvije prve greške za isti email u isto vrijeme: obje upisuju, a ON CONFLICT pusti jednu i drugu
	// tiho preskoči, umjesto da padne na duplom primarnom ključu
	@Modifying
	@Query(nativeQuery = true, value = """
			INSERT INTO login_failures (email, failures) VALUES (:email, 0)
			ON CONFLICT (email) DO NOTHING""")
	void insertIfMissing(String email);

	// SELECT ... FOR UPDATE: istovremene greške za isti email čekaju jedna drugu, pa se nijedna ne izgubi
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select f from LoginFailure f where f.email = :email")
	Optional<LoginFailure> findForUpdate(String email);

	@Modifying
	@Query("""
			delete from LoginFailure f
			where (f.lockedUntil is null or f.lockedUntil <= :sada)
			and (f.windowStart is null or f.windowStart <= :prozorOd)""")
	int deleteStale(Instant sada, Instant prozorOd);

}
