package ba.slotbook.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import ba.slotbook.resource.Resource;

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

	private static Booking naResursuSRokom(int sati) {
		Resource resurs = mock(Resource.class);
		when(resurs.getCancelNoticeHours()).thenReturn(sati);
		return new Booking(resurs, null, POCETAK, POCETAK.plusSeconds(3600), BookingStatus.CONFIRMED,
				POCETAK.minusSeconds(86400), null);
	}

	// Termin u 08:00 UTC, rok 2 sata: u 06:00:00 još može, u 06:00:01 ne
	@Test
	void klijentOtkazujeNajkasnijeNaRokIStatusOstajeKadZakasni() {
		Booking naVrijeme = naResursuSRokom(2);
		naVrijeme.otkaziKaoKlijent(POCETAK.minusSeconds(2 * 3600));
		assertThat(naVrijeme.getStatus()).isEqualTo(BookingStatus.CANCELLED);

		Booking prekasno = naResursuSRokom(2);
		assertThatThrownBy(() -> prekasno.otkaziKaoKlijent(POCETAK.minusSeconds(2 * 3600 - 1)))
				.isInstanceOf(CancelDeadlinePassedException.class)
				.hasFieldOrPropertyWithValue("deadline", POCETAK.minusSeconds(2 * 3600));
		assertThat(prekasno.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
	}

	@Test
	void bezRokaKlijentOtkazujeDoSamogPocetka() {
		Booking rezervacija = naResursuSRokom(0);

		rezervacija.otkaziKaoKlijent(POCETAK);

		assertThat(rezervacija.getStatus()).isEqualTo(BookingStatus.CANCELLED);
	}
}
