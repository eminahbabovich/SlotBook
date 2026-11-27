package ba.slotbook.ratelimit;

import java.time.Duration;

public class TooManyAttemptsException extends RuntimeException {

	// Šta je potrošeno: pokušaji s jedne adrese, pogrešne lozinke za jedan email, mailovi jednom
	// primaocu ili poruke asistentu u minuti. Od toga zavisi poruka.
	public enum Razlog {
		ADRESA, NALOG, POZIVNICA, PORUKE
	}

	private final Razlog razlog;
	private final Duration cekanje;

	public TooManyAttemptsException(Razlog razlog, Duration cekanje) {
		super("Previše pokušaja (" + razlog + "), sljedeći za " + cekanje);
		this.razlog = razlog;
		this.cekanje = cekanje;
	}

	public Razlog getRazlog() {
		return razlog;
	}

	// Naviše, iz nanosekundi: Retry-After od 0 bi klijenta odmah vratio na isti 429, a toMillis bi
	// čekanje kraće od milisekunde odsjekao na 0
	public long sekundiDoSljedeceg() {
		return (cekanje.toNanos() + 999_999_999) / 1_000_000_000;
	}

}
