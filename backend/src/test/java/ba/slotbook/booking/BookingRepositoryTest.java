package ba.slotbook.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import ba.slotbook.TestcontainersConfiguration;

// Preklapanje termina čuva baza. Redovi se upisuju SQL-om, mimo Jave, jer to i dokazujemo.
@Import(TestcontainersConfiguration.class)
@DataJpaTest
class BookingRepositoryTest {

	@Autowired
	JdbcTemplate jdbc;

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
		jdbc.update("insert into bookings values (gen_random_uuid(), ?, ?, ?::timestamptz, ?::timestamptz, ?, now())",
				resurs, klijent, od, doo, status);
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

	@Test
	void bazaOdbijaNepoznatStatus() {
		assertThatThrownBy(
				() -> rezervacija(stoBr1, emina, "2035-11-19 09:00+01", "2035-11-19 10:00+01", "ACTIVE"))
				.hasMessageContaining("bookings_status");
	}

}
