package ba.slotbook.auth;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "login_failures")
class LoginFailure {

	@Id
	private String email;

	@Column(nullable = false)
	private int failures;

	@Column(name = "window_start")
	private Instant windowStart;

	@Column(name = "locked_until")
	private Instant lockedUntil;

	protected LoginFailure() {
	}

	LoginFailure(String email) {
		this.email = email;
	}

	Duration lockedFor(Instant sada) {
		if (lockedUntil == null || !sada.isBefore(lockedUntil)) {
			return Duration.ZERO;
		}
		return Duration.between(sada, lockedUntil);
	}

	// Prozor od `trajanje` kreće od prve greške. Zaključavanje traje isto toliko od zadnje greške, pa je
	// prozor uvijek istekao kad zaključavanje prođe, i sljedeća greška kreće ispočetka.
	void recordFailure(Instant sada, int granica, Duration trajanje) {
		if (!lockedFor(sada).isZero()) {
			return;
		}
		if (windowStart == null || !sada.isBefore(windowStart.plus(trajanje))) {
			failures = 0;
			windowStart = sada;
		}
		failures++;
		if (failures >= granica) {
			lockedUntil = sada.plus(trajanje);
		}
	}

}
