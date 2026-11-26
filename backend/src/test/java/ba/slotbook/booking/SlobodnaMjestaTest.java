package ba.slotbook.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class SlobodnaMjestaTest {

	private static final Instant POCETAK = Instant.parse("2035-11-19T08:00:00Z");

	private static Booking naMjestu(int mjesto) {
		Booking rezervacija = new Booking(null, null, POCETAK, POCETAK.plusSeconds(3600), BookingStatus.CONFIRMED,
				POCETAK.minusSeconds(86400), null, Duration.ofHours(24));
		rezervacija.naMjesto(mjesto);
		return rezervacija;
	}

	@Test
	void slobodnaSuMjestaKojaNikoNeDrziOdNajnizeg() {
		assertThat(SlobodnaMjesta.slobodna(List.of(naMjestu(2)), 3)).containsExactly(1, 3);
		assertThat(SlobodnaMjesta.slobodna(List.of(), 2)).containsExactly(1, 2);
		assertThat(SlobodnaMjesta.slobodna(List.of(naMjestu(1), naMjestu(2)), 2)).isEmpty();
	}

	// Admin je salu s tri mjesta smanjio na jedno, a rezervacije na mjestima 2 i 3 ostaju. Termin je
	// i dalje pun: mjesto 1 nije slobodno samo zato što ga niko ne drži.
	@Test
	void poslijeSmanjenjaKapacitetaRezervacijeIznadNjegaIdaljeZauzimajuMjesta() {
		assertThat(SlobodnaMjesta.slobodna(List.of(naMjestu(2), naMjestu(3)), 1)).isEmpty();
		assertThat(SlobodnaMjesta.slobodna(List.of(naMjestu(3)), 2)).containsExactly(1);
	}

}
