package ba.slotbook.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.availability.AvailabilityInterval;
import ba.slotbook.availability.AvailabilityService;
import ba.slotbook.organization.OrganizationService;
import ba.slotbook.resource.ResourceService;
import ba.slotbook.user.UserService;

// Ista osoba u istom trenutku klikne osam različitih termina (dva taba, dupli klik po kalendaru).
// Bez locka resursa bi svaka nit izbrojala "imaš 0" i sve bi prošle.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BookingRulesIstovremenoTest {

	// Manje od 10, koliko Hikari daje konekcija, da niti čekaju na lock, a ne na konekciju
	private static final int NITI = 8;
	private static final int GRANICA = 3;
	private static final ZoneId ZONA = ZoneId.of("Europe/Sarajevo");

	@Autowired
	BookingService bookingService;

	@Autowired
	UserService userService;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	ResourceService resourceService;

	@Autowired
	AvailabilityService availabilityService;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void osamIstovremenihRezervacijaIsteOsobeSGranicomTriPustiTacnoTri() throws Exception {
		String sufiks = UUID.randomUUID().toString().substring(0, 6);
		UUID trener = userService.register("trener." + sufiks + "@example.com", "teretana-ilidza-7", "Trener").id();
		UUID emina = userService.register("emina." + sufiks + "@example.com", "teretana-ilidza-7", "Emina").id();
		UUID teretana = organizationService.create(trener, "Teretana granica", "teretana-granica-" + sufiks,
				ZONA.getId()).id();
		jdbc.update("insert into organization_members values (?, ?, 'CLIENT', now())", teretana, emina);
		UUID sala = resourceService.create(teretana, trener, "Sala 3", null, 60, true, 0, 1, 0).id();
		availabilityService.replace(sala, Arrays.stream(DayOfWeek.values())
				.map(d -> new AvailabilityInterval(d, LocalTime.of(8, 0), LocalTime.of(20, 0))).toList());
		jdbc.update("update resources set max_active_per_member = ? where id = ?", GRANICA, sala);

		CountDownLatch start = new CountDownLatch(1);
		List<Future<String>> rezultati = new ArrayList<>();
		try (ExecutorService niti = Executors.newFixedThreadPool(NITI)) {
			for (int i = 0; i < NITI; i++) {
				Instant termin = LocalDate.now(ZONA).plusDays(2).atTime(8 + i, 0).atZone(ZONA).toInstant();
				rezultati.add(niti.submit(() -> {
					start.await();
					try {
						bookingService.create(sala, emina, termin, null);
						return "OK";
					} catch (BookingRuleException e) {
						return e.getPravilo().name();
					} catch (RuntimeException e) {
						return e.getClass().getSimpleName();
					}
				}));
			}
			start.countDown();
		}

		List<String> ishodi = new ArrayList<>();
		for (Future<String> rezultat : rezultati) {
			ishodi.add(rezultat.get());
		}
		assertThat(ishodi).filteredOn("OK"::equals).hasSize(GRANICA);
		assertThat(ishodi).containsOnly("OK", "MAX_ACTIVE");
		assertThat(jdbc.queryForObject("select count(*) from bookings where client_id = ?", Long.class, emina))
				.isEqualTo(GRANICA);
	}

}
