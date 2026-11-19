package ba.slotbook.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.user.User;

// Zaključavanje prijave poslije previše pogrešnih lozinki za jedan email. Granica po IP adresi
// (AuthRateLimitInterceptor) ne štiti nalog od napadača koji mijenja adrese, ovo da.
@Service
public class LoginLockService {

	private final LoginFailureRepository failures;
	private final Clock clock;
	private final int granica;
	private final Duration trajanje;

	public LoginLockService(LoginFailureRepository failures, Clock clock,
			@Value("${slotbook.login-lock.failures}") int granica,
			@Value("${slotbook.login-lock.duration}") Duration trajanje) {
		this.failures = failures;
		this.clock = clock;
		this.granica = granica;
		this.trajanje = trajanje;
	}

	@Transactional(readOnly = true)
	public Duration lockedFor(String email) {
		return failures.findById(User.normalizeEmail(email))
				.map(f -> f.lockedFor(clock.instant()))
				.orElse(Duration.ZERO);
	}

	// Svoja kratka transakcija: red je zaključan samo dok traje upis, ne dok traje BCrypt
	@Transactional
	public void recordFailure(String email) {
		String normalizovan = User.normalizeEmail(email);
		failures.insertIfMissing(normalizovan);
		failures.findForUpdate(normalizovan).orElseThrow().recordFailure(clock.instant(), granica, trajanje);
	}

	@Transactional
	public void clear(String email) {
		failures.deleteById(User.normalizeEmail(email));
	}

	// Redovi za emailove koji se više ne javljaju, inače tabela raste sa svakim izmišljenim emailom
	@Scheduled(fixedDelay = 1, timeUnit = TimeUnit.HOURS)
	@Transactional
	public void ocisti() {
		Instant sada = clock.instant();
		failures.deleteStale(sada, sada.minus(trajanje));
	}

}
