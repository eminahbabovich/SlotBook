package ba.slotbook.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.organization.OrganizationService;
import ba.slotbook.resource.ResourceService;
import ba.slotbook.user.User;
import ba.slotbook.user.UserRepository;
import jakarta.persistence.EntityManager;

// Posao na dvije instance u isto vrijeme, bez ShedLocka. Prva transakcija uradi svoje i drži redove,
// druga krene i čeka na lock (to test provjeri u pg_stat_activity), pa tek onda prva završi.
// Tako je preklapanje sigurno, a ne slučajno kao kad se niti samo puste u isto vrijeme.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BookingJobIstovremenoTest {

	@Autowired
	BookingService service;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	ResourceService resourceService;

	@Autowired
	UserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PlatformTransactionManager transakcije;

	@Autowired
	EntityManager entityManager;

	private UUID studentica;
	private UUID sto;

	@BeforeEach
	void stoUCitaonici() {
		String oznaka = UUID.randomUUID().toString().substring(0, 8);
		UUID profesorica = users.save(new User("selma." + oznaka + "@example.com", "hash", "Selma",
				Instant.parse("2035-11-01T10:00:00Z"))).getId();
		studentica = users.save(new User("amra." + oznaka + "@example.com", "hash", "Amra",
				Instant.parse("2035-11-01T10:00:00Z"))).getId();
		UUID citaonica = organizationService.create(profesorica, "Čitaonica PMF", "citaonica-" + oznaka,
				"Europe/Sarajevo").id();
		sto = resourceService.create(citaonica, profesorica, "Sto br. 7", null, 60, true, 0).id();
	}

	private UUID upisi(String status, Instant pocetak, Instant rok) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into bookings (id, resource_id, client_id, start_time, end_time, status, created_at, "
				+ "expires_at) values (?, ?, ?, ?, ?, ?, now() - interval '3 days', ?)", id, sto, studentica,
				utc(pocetak), utc(pocetak.plus(1, ChronoUnit.HOURS)), status, rok == null ? null : utc(rok));
		return id;
	}

	private static OffsetDateTime utc(Instant trenutak) {
		return trenutak.atOffset(ZoneOffset.UTC);
	}

	// Prvi posao radi u otvorenoj transakciji, drugi u svojoj niti. Vraća drugi tek kad stvarno čeka na lock.
	private <T> Future<T> drugiDokPrviDrziRedove(Runnable prvi, java.util.concurrent.Callable<T> drugi,
			ExecutorService nit) {
		return new TransactionTemplate(transakcije).execute(s -> {
			prvi.run();
			Future<T> rezultat = nit.submit(drugi);
			long kraj = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
			while (jdbc.queryForObject("select count(*) from pg_stat_activity where wait_event_type = 'Lock' "
					+ "and datname = current_database()", Long.class) == 0) {
				assertThat(System.nanoTime()).as("drugi posao nikad nije čekao na lock").isLessThan(kraj);
				Thread.onSpinWait();
			}
			return rezultat;
		});
	}

	@Test
	void dvaPoslaIstovremenoIstekuSvakuRezervacijuSamoJednom() throws Exception {
		List<UUID> istekle = new ArrayList<>();
		for (int sat = 0; sat < 5; sat++) {
			istekle.add(upisi("PENDING", Instant.parse("2035-11-19T08:00:00Z").plus(sat, ChronoUnit.HOURS),
					Instant.now().minus(1, ChronoUnit.MINUTES)));
		}

		try (ExecutorService nit = Executors.newSingleThreadExecutor()) {
			Future<Integer> drugi = drugiDokPrviDrziRedove(service::expireOverdue, service::expireOverdue, nit);
			// Drugi je čekao red, pa ga ponovo provjerio: više nije PENDING, pa nema šta isteći
			assertThat(drugi.get(10, TimeUnit.SECONDS)).isZero();
		}

		for (UUID rezervacija : istekle) {
			assertThat(jdbc.queryForObject("select count(*) from booking_events where booking_id = ? and to_status = "
					+ "'EXPIRED'", Long.class, rezervacija)).isEqualTo(1);
			assertThat(jdbc.queryForObject("select count(*) from notifications where booking_id = ? and type = "
					+ "'BOOKING_EXPIRED'", Long.class, rezervacija)).isEqualTo(1);
		}
	}

	// Drugi ne vidi podsjetnik prvog (nije commitan), pa upisuje svoj i čeka na indeksu. Kad prvi
	// završi, drugi padne i cijeli njegov krug se poništi. Sljedeći krug ga više ne nađe.
	// flush: Hibernate inače upiše podsjetnik tek pri commitu, pa drugi ne bi imao na šta čekati.
	// U produkciji se isto sudare, samo pri commitu drugog.
	@Test
	void dvaPoslaIstovremenoDajuJedanPodsjetnikADrugiPadneNaIndeksu() throws Exception {
		UUID sutra = upisi("CONFIRMED", Instant.now().plus(5, ChronoUnit.HOURS), null);

		try (ExecutorService nit = Executors.newSingleThreadExecutor()) {
			Future<Integer> drugi = drugiDokPrviDrziRedove(() -> {
				service.sendReminders();
				entityManager.flush();
			}, service::sendReminders, nit);
			assertThatThrownBy(() -> drugi.get(10, TimeUnit.SECONDS)).rootCause()
					.hasMessageContaining("notifications_jedan_podsjetnik");
		}

		assertThat(jdbc.queryForObject("select count(*) from notifications where booking_id = ? and type = "
				+ "'BOOKING_REMINDER'", Long.class, sutra)).isEqualTo(1);
		service.sendReminders();
		assertThat(jdbc.queryForObject("select count(*) from notifications where booking_id = ? and type = "
				+ "'BOOKING_REMINDER'", Long.class, sutra)).isEqualTo(1);
	}

}
