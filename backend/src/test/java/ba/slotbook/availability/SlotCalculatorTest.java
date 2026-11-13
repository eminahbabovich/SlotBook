package ba.slotbook.availability;

import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SUNDAY;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

// Sarajevo: 25.10.2026. u 03:00 sat se vraća na 02:00 (+02 -> +01),
// a 28.3.2027. u 02:00 pomjera se na 03:00 (+01 -> +02)
class SlotCalculatorTest {

	private static final ZoneId SARAJEVO = ZoneId.of("Europe/Sarajevo");
	private static final Duration SAT = Duration.ofMinutes(60);
	// Daleko prije svih datuma u testovima, osim u testu koji provjerava prošle termine
	private static final Instant DAVNO = Instant.parse("2020-01-01T00:00:00Z");

	private AvailabilityInterval pravilo(DayOfWeek dan, String od, String doo) {
		return new AvailabilityInterval(dan, LocalTime.parse(od), LocalTime.parse(doo));
	}

	private TimeRange izuzetak(String od, String doo) {
		return new TimeRange(OffsetDateTime.parse(od).toInstant(), OffsetDateTime.parse(doo).toInstant());
	}

	private List<Instant> pocetci(SlotCalculator kalkulator, String od, String doIskljucivo, Instant sada) {
		return kalkulator.between(LocalDate.parse(od), LocalDate.parse(doIskljucivo), sada).stream()
				.map(TimeRange::start)
				.toList();
	}

	private static List<Instant> utc(String... vremena) {
		return java.util.Arrays.stream(vremena).map(Instant::parse).toList();
	}

	@Test
	void zimiJePonedjeljakUOsamSedamPoUtcu() {
		var kalkulator = new SlotCalculator(SARAJEVO, List.of(pravilo(MONDAY, "08:00", "12:00")), SAT, List.of());

		assertThat(pocetci(kalkulator, "2026-11-16", "2026-11-17", DAVNO))
				.isEqualTo(utc("2026-11-16T07:00:00Z", "2026-11-16T08:00:00Z", "2026-11-16T09:00:00Z",
						"2026-11-16T10:00:00Z"));
	}

	// Kriterij Faze 3: isto pravilo prije i poslije prelaska. Po zidnom satu je oba ponedjeljka
	// 08:00 i 09:00, a u UTC-u se termin pomjeri za sat.
	@Test
	void prelazakNaLjetnoVrijemeNePomjeraTerminPoLokalnomSatu() {
		var kalkulator = new SlotCalculator(SARAJEVO, List.of(pravilo(MONDAY, "08:00", "10:00")), SAT, List.of());

		assertThat(pocetci(kalkulator, "2027-03-22", "2027-03-30", DAVNO))
				.isEqualTo(utc("2027-03-22T07:00:00Z", "2027-03-22T08:00:00Z",
						"2027-03-29T06:00:00Z", "2027-03-29T07:00:00Z"));
	}

	@Test
	void povratakNaZimskoVrijemeNePomjeraTerminPoLokalnomSatu() {
		var kalkulator = new SlotCalculator(SARAJEVO, List.of(pravilo(MONDAY, "08:00", "09:00")), SAT, List.of());

		assertThat(pocetci(kalkulator, "2026-10-19", "2026-10-27", DAVNO))
				.isEqualTo(utc("2026-10-19T06:00:00Z", "2026-10-26T07:00:00Z"));
	}

	// Od 00:00 do 04:00 po satu na zidu, a sat 02:00-03:00 se desi dvaput: to je 5 stvarnih sati
	@Test
	void nocKadSeSatVracaImaPetSatnihTermina() {
		var kalkulator = new SlotCalculator(SARAJEVO, List.of(pravilo(SUNDAY, "00:00", "04:00")), SAT, List.of());

		List<TimeRange> termini = kalkulator.between(LocalDate.parse("2026-10-25"), LocalDate.parse("2026-10-26"),
				DAVNO);

		assertThat(termini).hasSize(5);
		assertThat(termini).allSatisfy(t -> assertThat(Duration.between(t.start(), t.end())).isEqualTo(SAT));
	}

	// Sat 02:00-03:00 ne postoji, pa je od 00:00 do 04:00 samo 3 stvarna sata
	@Test
	void nocKadSeSatPomjeraNaprijedImaTriSatnaTermina() {
		var kalkulator = new SlotCalculator(SARAJEVO, List.of(pravilo(SUNDAY, "00:00", "04:00")), SAT, List.of());

		assertThat(pocetci(kalkulator, "2027-03-28", "2027-03-29", DAVNO))
				.isEqualTo(utc("2027-03-27T23:00:00Z", "2027-03-28T00:00:00Z", "2027-03-28T01:00:00Z"));
	}

	@Test
	void terminKojiNeStaneCioUradnoVrijemeSeNeNudi() {
		var kalkulator = new SlotCalculator(SARAJEVO, List.of(pravilo(MONDAY, "08:00", "10:30")), SAT, List.of());

		assertThat(pocetci(kalkulator, "2026-11-16", "2026-11-17", DAVNO))
				.isEqualTo(utc("2026-11-16T07:00:00Z", "2026-11-16T08:00:00Z"));
	}

	// Izuzetak 09:30-10:30 zatvara termine 09 i 10. Onaj koji završava tačno u 11:00 ne dira termin od 11.
	@Test
	void izuzetakZatvaraSamoTermineKojeStvarnoPreklapa() {
		var kalkulator = new SlotCalculator(SARAJEVO, List.of(pravilo(MONDAY, "08:00", "13:00")), SAT, List.of(
				izuzetak("2026-11-16T09:30:00+01:00", "2026-11-16T10:30:00+01:00"),
				izuzetak("2026-11-16T10:30:00+01:00", "2026-11-16T11:00:00+01:00")));

		assertThat(pocetci(kalkulator, "2026-11-16", "2026-11-17", DAVNO))
				.isEqualTo(utc("2026-11-16T07:00:00Z", "2026-11-16T10:00:00Z", "2026-11-16T11:00:00Z"));
	}

	// U 09:30 se termin od 09:00 više ne nudi, jer je već počeo
	@Test
	void terminKojiJeVecPoceoSeNeNudi() {
		var kalkulator = new SlotCalculator(SARAJEVO, List.of(pravilo(MONDAY, "08:00", "12:00")), SAT, List.of());

		assertThat(pocetci(kalkulator, "2026-11-16", "2026-11-17", Instant.parse("2026-11-16T08:30:00Z")))
				.isEqualTo(utc("2026-11-16T09:00:00Z", "2026-11-16T10:00:00Z"));
	}

	// Pravila su poslana obrnutim redom, a termini dolaze po vremenu
	@Test
	void pauzaZaRucakIRedoslijedPoVremenu() {
		var kalkulator = new SlotCalculator(SARAJEVO, List.of(
				pravilo(MONDAY, "13:00", "15:00"),
				pravilo(MONDAY, "08:00", "10:00")), SAT, List.of());

		assertThat(pocetci(kalkulator, "2026-11-16", "2026-11-17", DAVNO))
				.isEqualTo(utc("2026-11-16T07:00:00Z", "2026-11-16T08:00:00Z", "2026-11-16T12:00:00Z",
						"2026-11-16T13:00:00Z"));
	}

	// Server radi u UTC-u, a račun ne smije zavisiti od njegove zone, nego samo od zone organizacije
	@Test
	void zonaOrganizacijeRazlicitaOdServerske() {
		var kalkulator = new SlotCalculator(ZoneId.of("Asia/Tokyo"), List.of(pravilo(MONDAY, "09:00", "10:00")), SAT,
				List.of());

		assertThat(pocetci(kalkulator, "2026-11-16", "2026-11-17", DAVNO)).isEqualTo(utc("2026-11-16T00:00:00Z"));
	}

	@Test
	void terminiOdTridesetMinutaIDanBezPravila() {
		var kalkulator = new SlotCalculator(SARAJEVO, List.of(pravilo(MONDAY, "08:00", "09:00")),
				Duration.ofMinutes(30), List.of());

		assertThat(pocetci(kalkulator, "2026-11-16", "2026-11-18", DAVNO))
				.isEqualTo(utc("2026-11-16T07:00:00Z", "2026-11-16T07:30:00Z"));
	}

}
