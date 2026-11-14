package ba.slotbook.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.availability.AvailabilityInterval;
import ba.slotbook.availability.AvailabilityService;
import ba.slotbook.error.InvalidFieldException;
import ba.slotbook.organization.OrganizationService;
import ba.slotbook.resource.ResourceService;
import ba.slotbook.user.User;
import ba.slotbook.user.UserRepository;

// Kroz pravu bazu, jer zauzet termin odbija EXCLUDE constraint, a ne Java
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BookingServiceTest {

	// Ponedjeljak, 19.11.2035. u 09:00 po sarajevskom vremenu (zimi +01:00)
	private static final Instant PONEDJELJAK_09 = Instant.parse("2035-11-19T08:00:00Z");

	@Autowired
	BookingService service;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	ResourceService resourceService;

	@Autowired
	AvailabilityService availabilityService;

	@Autowired
	UserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	private UUID profesorica;
	private UUID citaonica;
	private UUID studentica;

	@BeforeEach
	void citaonicaPonedjeljkomOdOsamDoDvanaest() {
		String oznaka = UUID.randomUUID().toString().substring(0, 8);
		profesorica = korisnik("amila." + oznaka + "@example.com", "Amila");
		studentica = korisnik("hana." + oznaka + "@example.com", "Hana");
		citaonica = organizationService.create(profesorica, "Čitaonica FIT", "citaonica-" + oznaka,
				"Europe/Sarajevo").id();
	}

	private UUID korisnik(String email, String ime) {
		return users.save(new User(email, "hash", ime, Instant.parse("2035-11-01T10:00:00Z"))).getId();
	}

	private UUID resurs(String naziv, int minuta, boolean autoConfirm) {
		UUID id = resourceService.create(citaonica, profesorica, naziv, null, minuta, autoConfirm).id();
		availabilityService.replace(id,
				List.of(new AvailabilityInterval(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))));
		return id;
	}

	@Test
	void naAutomatskojPotvrdiRezervacijaJePotvrdjenaAKrajIdeIzTrajanjaTermina() {
		UUID sto = resurs("Sto br. 3", 60, true);

		BookingResponse rezervacija = service.create(sto, studentica, PONEDJELJAK_09);

		assertThat(rezervacija.status()).isEqualTo(BookingStatus.CONFIRMED);
		assertThat(rezervacija.endTime()).isEqualTo(Instant.parse("2035-11-19T09:00:00Z"));
	}

	@Test
	void naRucnojPotvrdiRezervacijaCekaProvidera() {
		UUID konsultacije = resurs("Konsultacije, Baze podataka", 30, false);

		assertThat(service.create(konsultacije, studentica, PONEDJELJAK_09).status())
				.isEqualTo(BookingStatus.PENDING);
	}

	// 09:30 je u radnom vremenu, ali satni termini počinju u 08, 09, 10 i 11
	@Test
	void pocetakKojiNijePoravnatNaTerminSeOdbija() {
		UUID sto = resurs("Sto br. 3", 60, true);

		assertThatThrownBy(() -> service.create(sto, studentica, Instant.parse("2035-11-19T08:30:00Z")))
				.isInstanceOf(InvalidFieldException.class)
				.hasFieldOrPropertyWithValue("polje", "startTime");
	}

	@Test
	void terminVanRadnogVremenaIUProslostiSeOdbijaju() {
		UUID sto = resurs("Sto br. 3", 60, true);

		// 12:00 je kraj radnog vremena, a utorak nema pravila
		assertThatThrownBy(() -> service.create(sto, studentica, Instant.parse("2035-11-19T11:00:00Z")))
				.isInstanceOf(InvalidFieldException.class);
		assertThatThrownBy(() -> service.create(sto, studentica, Instant.parse("2035-11-20T08:00:00Z")))
				.isInstanceOf(InvalidFieldException.class);
		// Ponedjeljak 16.11.2020. u 09:00, pravi termin, ali prošao
		assertThatThrownBy(() -> service.create(sto, studentica, Instant.parse("2020-11-16T08:00:00Z")))
				.isInstanceOf(InvalidFieldException.class);
	}

	@Test
	void zauzetTerminDajeSlotTakenIOstajeJednaRezervacija() {
		UUID sto = resurs("Sto br. 3", 60, true);
		UUID drugaStudentica = korisnik("lamija." + UUID.randomUUID() + "@example.com", "Lamija");
		service.create(sto, studentica, PONEDJELJAK_09);

		assertThatThrownBy(() -> service.create(sto, drugaStudentica, PONEDJELJAK_09))
				.isInstanceOf(SlotTakenException.class);
		assertThat(jdbc.queryForObject("select count(*) from bookings where resource_id = ?", Long.class, sto))
				.isEqualTo(1);
	}

}
