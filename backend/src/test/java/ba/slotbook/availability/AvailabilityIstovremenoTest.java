package ba.slotbook.availability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
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
import ba.slotbook.organization.OrganizationService;
import ba.slotbook.resource.ResourceService;
import ba.slotbook.user.User;
import ba.slotbook.user.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AvailabilityIstovremenoTest {

	// Manje od 10, koliko Hikari daje konekcija, da niti čekaju na zaključan red, a ne na konekciju
	private static final int NITI = 8;

	@Autowired
	AvailabilityService service;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	ResourceService resourceService;

	@Autowired
	UserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	// Svaka nit šalje drugu sedmicu s jednim intervalom. Bez zaključavanja bi se brisanja i upisi
	// ispreplitali i ostalo bi više intervala, mješavina nekoliko zahtjeva.
	@Test
	void istovremeneIzmjeneRadnogVremenaOstaveSedmicuIzTacnoJednogZahtjeva() throws Exception {
		User emir = users.save(new User("emir.istovremeno@example.com", "hash", "Emir",
				Instant.parse("2026-11-13T10:00:00Z")));
		UUID citaonica = organizationService.create(emir.getId(), "Čitaonica Vijećnica", "citaonica-istovremeno",
				"Europe/Sarajevo").id();
		UUID sto = resourceService.create(citaonica, emir.getId(), "Sto 12", null, 60).id();

		CountDownLatch start = new CountDownLatch(1);
		List<Future<String>> rezultati = new ArrayList<>();
		try (ExecutorService niti = Executors.newFixedThreadPool(NITI)) {
			for (int i = 0; i < NITI; i++) {
				LocalTime pocetak = LocalTime.of(8 + i, 0);
				rezultati.add(niti.submit(() -> {
					start.await();
					try {
						service.replace(sto, List.of(
								new AvailabilityInterval(DayOfWeek.MONDAY, pocetak, pocetak.plusMinutes(30))));
						return "OK";
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
		assertThat(ishodi).containsOnly("OK");
		assertThat(jdbc.queryForObject("select count(*) from availability_rules where resource_id = ?", Long.class,
				sto)).isEqualTo(1);
	}

}
