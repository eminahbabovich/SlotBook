package ba.slotbook.ratelimit;

import java.time.Duration;

public class TooManyAttemptsException extends RuntimeException {

	private final Duration cekanje;

	public TooManyAttemptsException(Duration cekanje) {
		super("Previše pokušaja, sljedeći za " + cekanje);
		this.cekanje = cekanje;
	}

	// Naviše, iz nanosekundi: Retry-After od 0 bi klijenta odmah vratio na isti 429, a toMillis bi
	// čekanje kraće od milisekunde odsjekao na 0
	public long sekundiDoSljedeceg() {
		return (cekanje.toNanos() + 999_999_999) / 1_000_000_000;
	}

}
