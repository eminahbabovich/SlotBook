package ba.slotbook.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class BookingTest {

	private static final Instant POCETAK = Instant.parse("2035-11-19T08:00:00Z");

	private static Booking u(BookingStatus status) {
		return new Booking(null, null, POCETAK, POCETAK.plusSeconds(3600), status, POCETAK.minusSeconds(86400),
				null);
	}

	// Svih 49 parova: prolazi tačno 7 iz tabele u PROJECT_PLAN.md, a ostali daju BookingTransitionException.
	// Ako neko doda prelaz u kod a ne u tabelu (ili obrnuto), ovaj test to javi.
	@Test
	void dozvoljeniSuTacnoPrelaziIzTabeleUPlanu() {
		Set<String> dozvoljeni = new HashSet<>();
		for (BookingStatus iz : BookingStatus.values()) {
			for (BookingStatus u : BookingStatus.values()) {
				Booking rezervacija = u(iz);
				try {
					rezervacija.prijedi(u, POCETAK.plusSeconds(7200));
					assertThat(rezervacija.getStatus()).isEqualTo(u);
					dozvoljeni.add(iz + "->" + u);
				} catch (BookingTransitionException e) {
					assertThat(rezervacija.getStatus()).isEqualTo(iz);
				}
			}
		}

		assertThat(dozvoljeni).containsExactlyInAnyOrder("PENDING->CONFIRMED", "PENDING->REJECTED",
				"PENDING->EXPIRED", "PENDING->CANCELLED", "CONFIRMED->CANCELLED", "CONFIRMED->COMPLETED",
				"CONFIRMED->NO_SHOW");
	}

	@Test
	void nedolazakSeNeMozeOznacitiPrijePocetkaTermina() {
		Booking rezervacija = u(BookingStatus.CONFIRMED);

		assertThatThrownBy(() -> rezervacija.prijedi(BookingStatus.NO_SHOW, POCETAK.minusSeconds(60)))
				.isInstanceOf(NoShowTooEarlyException.class);
		assertThat(rezervacija.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

		rezervacija.prijedi(BookingStatus.NO_SHOW, POCETAK);
		assertThat(rezervacija.getStatus()).isEqualTo(BookingStatus.NO_SHOW);
	}

}
