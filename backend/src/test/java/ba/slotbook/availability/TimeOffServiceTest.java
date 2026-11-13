package ba.slotbook.availability;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import ba.slotbook.error.InvalidFieldException;

class TimeOffServiceTest {

	private void provjeri(String pocetak, String kraj) {
		TimeOffService.provjeri(OffsetDateTime.parse(pocetak), OffsetDateTime.parse(kraj));
	}

	// Kraj je napisan u UTC-u i "ranije" po satu (09:30 < 10:00), ali je kasniji trenutak
	@Test
	void porediSeTrenutakANeSatUTekstu() {
		assertThatCode(() -> provjeri("2026-12-20T10:00:00+01:00", "2026-12-20T09:30:00Z"))
				.doesNotThrowAnyException();
	}

	// 10:15 po ljetnom vremenu (+02:00) je 08:15 UTC, prije početka u 09:00 UTC
	@Test
	void krajKojiJeKasnijiSamoPoSatuSeOdbija() {
		assertThatThrownBy(() -> provjeri("2026-12-20T10:00:00+01:00", "2026-12-20T10:15:00+02:00"))
				.isInstanceOf(InvalidFieldException.class)
				.extracting("polje").isEqualTo("endsAt");
	}

	// Noć prelaska na ljetno vrijeme: od subote 22:00 do nedjelje 22:00 je 23 sata, i to je ispravno
	@Test
	void izuzetakPrekoPrelaskaNaLjetnoVrijemeProlazi() {
		assertThatCode(() -> provjeri("2027-03-27T22:00:00+01:00", "2027-03-28T22:00:00+02:00"))
				.doesNotThrowAnyException();
	}

	@Test
	void izuzetakBezTrajanjaSeOdbija() {
		assertThatThrownBy(() -> provjeri("2026-12-24T00:00:00+01:00", "2026-12-23T23:00:00Z"))
				.isInstanceOf(InvalidFieldException.class);
	}

}
