package ba.slotbook.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.resource.Resource;
import ba.slotbook.user.User;
import jakarta.persistence.EntityManager;

// Preklapanje termina čuva baza. Redovi se upisuju SQL-om, mimo Jave, jer to i dokazujemo.
@Import(TestcontainersConfiguration.class)
@DataJpaTest
class BookingRepositoryTest {

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	BookingRepository bookings;

	@Autowired
	EntityManager em;

	private UUID stoBr1;
	private UUID stoBr2;
	private UUID emina;
	private UUID dzenan;

	@BeforeEach
	void citaonicaSaDvaStola() {
		UUID selma = korisnik("selma@example.com", "Selma");
		emina = korisnik("emina@example.com", "Emina");
		dzenan = korisnik("dzenan@example.com", "Dženan");
		UUID citaonica = UUID.randomUUID();
		jdbc.update("insert into organizations values (?, 'Čitaonica FIT', 'citaonica-rezervacije', "
				+ "'Europe/Sarajevo', now())", citaonica);
		jdbc.update("insert into organization_members values (?, ?, 'PROVIDER', now())", citaonica, selma);
		stoBr1 = resurs(citaonica, selma, "Sto br. 1");
		stoBr2 = resurs(citaonica, selma, "Sto br. 2");
	}

	private UUID korisnik(String email, String ime) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into users values (?, ?, 'hash', ?, now())", id, email, ime);
		return id;
	}

	private UUID resurs(UUID organizacija, UUID provider, String naziv) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into resources (id, organization_id, provider_id, name, slot_minutes, created_at) "
				+ "values (?, ?, ?, ?, 60, now())", id, organizacija, provider, naziv);
		return id;
	}

	private void rezervacija(UUID resurs, UUID klijent, String od, String doo, String status) {
		jdbc.update("insert into bookings (id, resource_id, client_id, start_time, end_time, status, created_at, "
				+ "expires_at) values (gen_random_uuid(), ?, ?, ?::timestamptz, ?::timestamptz, ?, now(), ?::timestamptz)",
				resurs, klijent, od, doo, status, od);
	}

	@Test
	void bazaOdbijaPreklapanjeAktivnihRezervacijaIstogResursa() {
		rezervacija(stoBr1, emina, "2035-11-19 09:00+01", "2035-11-19 10:00+01", "CONFIRMED");

		assertThatThrownBy(
				() -> rezervacija(stoBr1, dzenan, "2035-11-19 09:30+01", "2035-11-19 10:30+01", "PENDING"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("bookings_bez_preklapanja");
	}

	@Test
	void terminiKojiSeDodirujuIIstoVrijemeNaDrugomResursuProlaze() {
		rezervacija(stoBr1, emina, "2035-11-19 09:00+01", "2035-11-19 10:00+01", "CONFIRMED");
		rezervacija(stoBr1, dzenan, "2035-11-19 10:00+01", "2035-11-19 11:00+01", "CONFIRMED");
		rezervacija(stoBr2, dzenan, "2035-11-19 09:00+01", "2035-11-19 10:00+01", "PENDING");

		assertThat(jdbc.queryForObject("select count(*) from bookings", Long.class)).isEqualTo(3);
	}

	// Isti trenutak zapisan u drugoj zoni je i dalje preklapanje: 08:30 UTC je 09:30 u Sarajevu
	@Test
	void preklapanjeSePorediPoTrenutkuANePoSatu() {
		rezervacija(stoBr1, emina, "2035-11-19 09:00+01", "2035-11-19 10:00+01", "CONFIRMED");

		assertThatThrownBy(
				() -> rezervacija(stoBr1, dzenan, "2035-11-19 08:30+00", "2035-11-19 09:30+00", "PENDING"))
				.hasMessageContaining("bookings_bez_preklapanja");
	}

	@Test
	void otkazanaOdbijenaIIsteklaRezervacijaNeDrzeTermin() {
		rezervacija(stoBr1, emina, "2035-11-19 09:00+01", "2035-11-19 10:00+01", "CANCELLED");
		rezervacija(stoBr1, emina, "2035-11-19 09:00+01", "2035-11-19 10:00+01", "REJECTED");
		rezervacija(stoBr1, emina, "2035-11-19 09:00+01", "2035-11-19 10:00+01", "EXPIRED");
		rezervacija(stoBr1, dzenan, "2035-11-19 09:00+01", "2035-11-19 10:00+01", "CONFIRMED");

		assertThat(jdbc.queryForObject("select count(*) from bookings", Long.class)).isEqualTo(4);
	}

	// Svaki odbijen INSERT u svom testu: poslije greške Postgres ne izvršava ništa do kraja transakcije
	@Test
	void bazaOdbijaKrajPrijePocetka() {
		assertThatThrownBy(
				() -> rezervacija(stoBr1, emina, "2035-11-19 10:00+01", "2035-11-19 09:00+01", "PENDING"))
				.hasMessageContaining("bookings_pocetak_prije_kraja");
	}

	// Raspored providera se oslanja na ovu granicu (findSchedule), pa je čuva baza, a ne samo dužina termina u Javi
	@Test
	void bazaOdbijaRezervacijuDuzuOdOsamSati() {
		rezervacija(stoBr1, emina, "2035-11-19 08:00+01", "2035-11-19 16:00+01", "CONFIRMED");

		assertThatThrownBy(
				() -> rezervacija(stoBr2, emina, "2035-11-19 08:00+01", "2035-11-19 16:01+01", "PENDING"))
				.hasMessageContaining("bookings_najvise_osam_sati");
	}

	// Noćna smjena od 20 do 04 pripada i rasporedu za 19. novembar, iako je počela dan ranije.
	// Ona koja završi tačno u ponoć ne pripada, jer je kraj isključen.
	@Test
	void rasporedVidiRezervacijuKojaJePocelaPrethodnogDana() {
		rezervacija(stoBr1, emina, "2035-11-18 16:00+01", "2035-11-19 00:00+01", "COMPLETED");
		rezervacija(stoBr1, dzenan, "2035-11-18 20:00+01", "2035-11-19 04:00+01", "CONFIRMED");
		rezervacija(stoBr1, emina, "2035-11-19 09:00+01", "2035-11-19 10:00+01", "CONFIRMED");

		List<Booking> raspored = bookings.findSchedule(stoBr1, Instant.parse("2035-11-18T23:00:00Z"),
				Instant.parse("2035-11-19T23:00:00Z"));

		assertThat(raspored).extracting(Booking::getStartTime).containsExactly(
				Instant.parse("2035-11-18T19:00:00Z"), Instant.parse("2035-11-19T08:00:00Z"));
	}

	// Rezervacija ne može nastati kao otkazana, i kad neko upiše historiju mimo Jave
	@Test
	void historijaNePrimaNastanakUZavrsnomStanju() {
		rezervacija(stoBr1, emina, "2035-11-19 09:00+01", "2035-11-19 10:00+01", "CANCELLED");
		UUID booking = jdbc.queryForObject("select id from bookings", UUID.class);

		assertThatThrownBy(() -> jdbc.update("insert into booking_events values (gen_random_uuid(), ?, null, "
				+ "'CANCELLED', ?, now())", booking, emina))
				.hasMessageContaining("booking_events_nastanak");
	}

	@Test
	void bezAkteraMoguSamoIstekIZatvaranje() {
		UUID booking = UUID.randomUUID();
		jdbc.update("insert into bookings (id, resource_id, client_id, start_time, end_time, status, created_at) "
				+ "values (?, ?, ?, '2035-11-19 09:00+01', '2035-11-19 10:00+01', 'CONFIRMED', now())", booking, stoBr1,
				emina);
		jdbc.update("insert into booking_events values (gen_random_uuid(), ?, 'CONFIRMED', 'COMPLETED', null, now())",
				booking);

		assertThatThrownBy(() -> jdbc.update("insert into booking_events values (gen_random_uuid(), ?, "
				+ "'CONFIRMED', 'CANCELLED', null, now())", booking))
				.hasMessageContaining("booking_events_akter");
	}

	@Test
	void bazaOdbijaNepoznatStatus() {
		assertThatThrownBy(
				() -> rezervacija(stoBr1, emina, "2035-11-19 09:00+01", "2035-11-19 10:00+01", "ACTIVE"))
				.hasMessageContaining("bookings_status");
	}

	private Booking nova(UUID resurs, UUID klijent, String od, String doo) {
		return new Booking(em.getReference(Resource.class, resurs), em.getReference(User.class, klijent),
				Instant.parse(od), Instant.parse(doo), BookingStatus.CONFIRMED, Instant.parse("2035-11-18T12:00:00Z"),
				null, Duration.ofHours(24));
	}

	@Test
	void entitetSeUpisujeSaStatusomKaoTekstom() {
		Booking b = bookings.saveAndFlush(nova(stoBr1, emina, "2035-11-19T08:00:00Z", "2035-11-19T09:00:00Z"));

		assertThat(jdbc.queryForObject("select status from bookings where id = ?", String.class, b.getId()))
				.isEqualTo("CONFIRMED");
		assertThat(jdbc.queryForObject("select start_time = '2035-11-19 09:00+01' from bookings where id = ?",
				Boolean.class, b.getId())).isTrue();
	}

	// Servis u sljedećem koraku od ovog izuzetka pravi 409, pa mora stići i kad upis ide kroz JPA
	@Test
	void preklapanjeKrozRepozitorijDajeDataIntegrityViolation() {
		bookings.saveAndFlush(nova(stoBr1, emina, "2035-11-19T08:00:00Z", "2035-11-19T09:00:00Z"));

		assertThatThrownBy(
				() -> bookings.saveAndFlush(nova(stoBr1, dzenan, "2035-11-19T08:30:00Z", "2035-11-19T09:30:00Z")))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("bookings_bez_preklapanja");
	}

	private void rezervacijaSRokom(String status, String rok) {
		jdbc.update("insert into bookings (id, resource_id, client_id, start_time, end_time, status, created_at, "
				+ "expires_at) values (gen_random_uuid(), ?, ?, '2035-11-19 09:00+01', '2035-11-19 10:00+01', ?, now(), "
				+ "?::timestamptz)", stoBr1, emina, status, rok);
	}

	@Test
	void bazaOdbijaNepotvrdjenuRezervacijuBezRoka() {
		assertThatThrownBy(() -> rezervacijaSRokom("PENDING", null))
				.hasMessageContaining("bookings_pending_ima_rok");
	}

	@Test
	void bazaOdbijaRokZaPotvrduPoslijePocetkaTermina() {
		assertThatThrownBy(() -> rezervacijaSRokom("PENDING", "2035-11-19 09:00:01+01"))
				.hasMessageContaining("bookings_rok_do_pocetka");
	}
}
