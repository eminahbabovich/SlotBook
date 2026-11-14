package ba.slotbook.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class InviteIstovremenoTest {

	private static final int NITI = 8;
	private static final int KRUGOVA = 5;

	@Autowired
	OrganizationInviteService service;

	@Autowired
	OrganizationRepository organizations;

	@Autowired
	JdbcTemplate jdbc;

	// Bez prevođenja greške indeksa dio niti dobije DataIntegrityViolationException, tj. 500.
	// Pet krugova, jer se u jednom krugu može desiti da niti stignu jedna za drugom bez sudara.
	@Test
	void istovremenePozivniceZaIstiEmailDajuJednuPozivnicuIJedanMail() throws Exception {
		Organization teretana = organizations.save(new Organization("Teretana Grbavica", "teretana-istovremeno",
				"Europe/Sarajevo", Instant.parse("2026-11-14T10:00:00Z")));
		List<String> ishodi = new ArrayList<>();

		for (int krug = 0; krug < KRUGOVA; krug++) {
			String email = "edin.istovremeno" + krug + "@example.com";
			CountDownLatch start = new CountDownLatch(1);
			List<Future<String>> rezultati = new ArrayList<>();
			try (ExecutorService niti = Executors.newFixedThreadPool(NITI)) {
				for (int i = 0; i < NITI; i++) {
					rezultati.add(niti.submit(() -> {
						start.await();
						try {
							service.invite(teretana.getId(), email, OrganizationRole.CLIENT);
							return "OK";
						} catch (RuntimeException e) {
							return e.getClass().getSimpleName();
						}
					}));
				}
				start.countDown();
				for (Future<String> rezultat : rezultati) {
					ishodi.add(rezultat.get());
				}
			}
			assertThat(jdbc.queryForObject("select count(*) from organization_invites where email = ?", Long.class,
					email)).isEqualTo(1);
			assertThat(jdbc.queryForObject("select count(*) from notifications where email = ?", Long.class, email))
					.isEqualTo(1);
		}

		assertThat(ishodi).containsOnly("OK", "InviteRaceException");
	}

}
