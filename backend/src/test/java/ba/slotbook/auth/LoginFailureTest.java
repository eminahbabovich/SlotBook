package ba.slotbook.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class LoginFailureTest {

	private static final int GRANICA = 5;
	private static final Duration TRAJANJE = Duration.ofMinutes(15);

	private final Instant osam = Instant.parse("2035-11-19T08:00:00Z");
	private final LoginFailure greske = new LoginFailure("amila@example.com");

	private void pogresna(int puta, Instant kad) {
		for (int i = 0; i < puta; i++) {
			greske.recordFailure(kad, GRANICA, TRAJANJE);
		}
	}

	@Test
	void petaPogresnaLozinkaZakljucavaNaPetnaestMinuta() {
		pogresna(4, osam);
		assertThat(greske.lockedFor(osam)).isZero();

		pogresna(1, osam.plusSeconds(60));

		assertThat(greske.lockedFor(osam.plusSeconds(60))).isEqualTo(TRAJANJE);
		assertThat(greske.lockedFor(osam.plusSeconds(60 + 15 * 60))).isZero();
	}

	// Prozor kreće od prve greške: četiri greške pa pauza duža od 15 minuta, i brojanje kreće ispočetka
	@Test
	void greskeStarijeOdPetnaestMinutaSeNeBroje() {
		pogresna(4, osam);
		Instant kasnije = osam.plus(TRAJANJE).plusSeconds(1);

		pogresna(4, kasnije);
		assertThat(greske.lockedFor(kasnije)).isZero();

		pogresna(1, kasnije);
		assertThat(greske.lockedFor(kasnije)).isEqualTo(TRAJANJE);
	}

	// Inače bi prva pogrešna lozinka poslije isteka odmah opet zaključala, jer je brojač ostao na pet
	@Test
	void poslijeZakljucavanjaBrojanjeKreceIspocetka() {
		pogresna(5, osam);
		Instant poslije = osam.plus(TRAJANJE);

		pogresna(4, poslije);

		assertThat(greske.lockedFor(poslije)).isZero();
	}

	// Greška dok je zaključano se ne broji: zahtjev do nje ni ne dođe, ali i kad dođe (istovremeni
	// zahtjevi), zaključavanje se ne smije produžiti
	@Test
	void greskaDokJeZakljucanoNeProduzavaZakljucavanje() {
		pogresna(5, osam);

		pogresna(5, osam.plusSeconds(600));

		assertThat(greske.lockedFor(osam.plusSeconds(600))).isEqualTo(Duration.ofMinutes(5));
	}

}
