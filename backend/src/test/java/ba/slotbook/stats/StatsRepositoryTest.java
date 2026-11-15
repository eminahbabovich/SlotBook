package ba.slotbook.stats;

import static ba.slotbook.booking.BookingStatus.CANCELLED;
import static ba.slotbook.booking.BookingStatus.CONFIRMED;
import static ba.slotbook.booking.BookingStatus.NO_SHOW;
import static ba.slotbook.booking.BookingStatus.PENDING;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import ba.slotbook.TestcontainersConfiguration;

// Očekivane brojke su prebrojane ručno iz redova u testu, ne izračunate upitom koji testiramo
@Import(TestcontainersConfiguration.class)
@DataJpaTest
class StatsRepositoryTest {

	// Novembar 2035. u Sarajevu, ponoć do ponoći. Zimsko vrijeme je od 28.10., pa je cijeli mjesec +01.
	private static final Instant NOVEMBAR_OD = OffsetDateTime.parse("2035-11-01T00:00+01:00").toInstant();
	private static final Instant NOVEMBAR_DO = OffsetDateTime.parse("2035-12-01T00:00+01:00").toInstant();

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	StatsRepository stats;

	private UUID selma;
	private UUID citaonica;
	private UUID teretana;
	private UUID stoBr1;
	private UUID stoBr2;
	private UUID salaZaJogu;
	private UUID emina;

	@BeforeEach
	void citaonicaITeretana() {
		selma = korisnik("selma@example.com", "Selma");
		UUID tarik = korisnik("tarik@example.com", "Tarik");
		emina = korisnik("emina@example.com", "Emina");

		citaonica = organizacija("Čitaonica FIT", "citaonica-fit", selma);
		stoBr1 = resurs(citaonica, selma, "Sto br. 1");
		stoBr2 = resurs(citaonica, selma, "Sto br. 2");

		teretana = organizacija("Teretana Ćelik", "teretana-celik", tarik);
		salaZaJogu = resurs(teretana, tarik, "Sala za jogu");
	}

	@Test
	void brojiRezervacijePoStatusuUPerioduSamoZaSvojuOrganizaciju() {
		rezervacija(stoBr1, "2035-11-03 09:00+01", "CONFIRMED");
		rezervacija(stoBr1, "2035-11-04 09:00+01", "CONFIRMED");
		rezervacija(stoBr1, "2035-11-05 09:00+01", "CONFIRMED");
		// Otkazana ne drži termin, pa smije stajati na istom mjestu kao potvrđena
		rezervacija(stoBr1, "2035-11-05 09:00+01", "CANCELLED");
		rezervacija(stoBr1, "2035-11-06 09:00+01", "CANCELLED");
		rezervacija(stoBr1, "2035-11-07 09:00+01", "NO_SHOW");
		rezervacija(stoBr2, "2035-11-03 09:00+01", "PENDING");
		// Granice: počinje tačno na početku perioda (ulazi), tačno na kraju (ne ulazi), i pola sata
		// prije početka iako završava u novembru (ne ulazi, gleda se početak)
		rezervacija(stoBr2, "2035-11-01 00:00+01", "CONFIRMED");
		rezervacija(stoBr2, "2035-12-01 00:00+01", "CONFIRMED");
		rezervacija(stoBr1, "2035-10-31 23:30+01", "CONFIRMED");
		// Tuđa organizacija, isti mjesec
		rezervacija(salaZaJogu, "2035-11-10 18:00+01", "CONFIRMED");

		assertThat(stats.countByStatus(citaonica, NOVEMBAR_OD, NOVEMBAR_DO)).containsExactlyInAnyOrder(
				new StatusCount(CONFIRMED, 4),
				new StatusCount(CANCELLED, 2),
				new StatusCount(NO_SHOW, 1),
				new StatusCount(PENDING, 1));
	}

	@Test
	void providerVidiSamoSvojeResurseISamoUTrazenojOrganizaciji() {
		// Druga osoba u istoj čitaonici, s konsultacijama u svom kabinetu
		UUID amra = korisnik("amra@example.com", "Amra");
		clan(citaonica, amra);
		UUID kabinet = resurs(citaonica, amra, "Kabinet 2-14");
		// Selma vodi i pilates u teretani, pa je provider u dvije organizacije
		clan(teretana, selma);
		UUID pilates = resurs(teretana, selma, "Sala za pilates");

		rezervacija(stoBr1, "2035-11-03 09:00+01", "CONFIRMED");
		rezervacija(stoBr2, "2035-11-04 09:00+01", "CONFIRMED");
		rezervacija(stoBr1, "2035-11-05 09:00+01", "CANCELLED");
		rezervacija(kabinet, "2035-11-03 12:00+01", "CONFIRMED");
		rezervacija(kabinet, "2035-11-04 12:00+01", "NO_SHOW");
		rezervacija(pilates, "2035-11-06 17:00+01", "CONFIRMED");

		assertThat(stats.countByStatusForProvider(citaonica, selma, NOVEMBAR_OD, NOVEMBAR_DO))
				.containsExactlyInAnyOrder(
						new StatusCount(CONFIRMED, 2),
						new StatusCount(CANCELLED, 1));
	}

	private void clan(UUID organizacija, UUID korisnik) {
		jdbc.update("insert into organization_members values (?, ?, 'PROVIDER', now())", organizacija, korisnik);
	}

	private UUID korisnik(String email, String ime) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into users values (?, ?, 'hash', ?, now())", id, email, ime);
		return id;
	}

	private UUID organizacija(String naziv, String slug, UUID provider) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into organizations values (?, ?, ?, 'Europe/Sarajevo', now())", id, naziv, slug);
		clan(id, provider);
		return id;
	}

	private UUID resurs(UUID organizacija, UUID provider, String naziv) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into resources (id, organization_id, provider_id, name, slot_minutes, created_at) "
				+ "values (?, ?, ?, ?, 60, now())", id, organizacija, provider, naziv);
		return id;
	}

	private void rezervacija(UUID resurs, String pocetak, String status) {
		jdbc.update("insert into bookings (id, resource_id, client_id, start_time, end_time, status, created_at, "
				+ "expires_at) values (gen_random_uuid(), ?, ?, ?::timestamptz, ?::timestamptz + interval '1 hour', "
				+ "?, now(), ?::timestamptz)", resurs, emina, pocetak, pocetak, status, pocetak);
	}

}
