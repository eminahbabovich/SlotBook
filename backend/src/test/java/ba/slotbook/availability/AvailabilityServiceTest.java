package ba.slotbook.availability;

import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.TUESDAY;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import ba.slotbook.error.InvalidFieldException;

class AvailabilityServiceTest {

	private AvailabilityInterval interval(DayOfWeek dan, String od, String doo) {
		return new AvailabilityInterval(dan, LocalTime.parse(od), LocalTime.parse(doo));
	}

	@Test
	void radniDanSPauzomZaRucakProlazi() {
		assertThatCode(() -> AvailabilityService.provjeri(List.of(
				interval(MONDAY, "08:00", "12:00"),
				interval(MONDAY, "12:00", "16:00"),
				interval(TUESDAY, "08:00", "16:00")))).doesNotThrowAnyException();
	}

	@Test
	void krajPrijePocetkaSeOdbijaUzPoljeKraja() {
		assertThatThrownBy(() -> AvailabilityService.provjeri(List.of(
				interval(MONDAY, "08:00", "12:00"),
				interval(TUESDAY, "22:00", "02:00"))))
				.isInstanceOf(InvalidFieldException.class)
				.extracting("polje").isEqualTo("rules[1].end");
	}

	@Test
	void intervalKojiTrajeNulaMinutaSeOdbija() {
		assertThatThrownBy(() -> AvailabilityService.provjeri(List.of(interval(MONDAY, "08:00", "08:00"))))
				.isInstanceOf(InvalidFieldException.class);
	}

	// Redoslijed u zahtjevu nije bitan: drugi interval počinje prije prvog, a preklapaju se
	@Test
	void preklapanjeIstogDanaSeOdbijaUzKasnijiInterval() {
		assertThatThrownBy(() -> AvailabilityService.provjeri(List.of(
				interval(MONDAY, "10:00", "14:00"),
				interval(TUESDAY, "10:00", "14:00"),
				interval(MONDAY, "08:00", "10:30"))))
				.isInstanceOf(InvalidFieldException.class)
				.extracting("polje").isEqualTo("rules[2]");
	}

}
