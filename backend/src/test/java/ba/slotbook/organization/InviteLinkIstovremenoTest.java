package ba.slotbook.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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
import ba.slotbook.user.User;
import ba.slotbook.user.UserRepository;

// Plakat u čitaonici, link za pet ljudi, a dvadeset ih skenira kod u istom trenutku
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class InviteLinkIstovremenoTest {

	private static final int NITI = 20;
	private static final int MJESTA = 5;

	@Autowired
	InviteLinkService service;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	UserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void dvadesetIstovremenihUlazakaNaLinkSPetMjestaPustiTacnoPet() throws Exception {
		Instant sada = Instant.now();
		User admin = users.save(new User("selma.plakat@example.com", "hash", "Selma Hadžić", sada));
		UUID organizacija = organizationService.create(admin.getId(), "Čitaonica Plakat", "citaonica-plakat",
				"Europe/Sarajevo").id();
		String url = service.create(organizacija, admin.getId(), MJESTA, 7).url();
		String token = url.substring(url.lastIndexOf('/') + 1);
		List<User> studenti = new ArrayList<>();
		for (int i = 0; i < NITI; i++) {
			User student = new User("student" + i + ".plakat@example.com", "hash", "Student " + i, sada);
			student.verifyEmail(sada);
			studenti.add(users.save(student));
		}

		CountDownLatch start = new CountDownLatch(1);
		List<Future<String>> rezultati = new ArrayList<>();
		try (ExecutorService niti = Executors.newFixedThreadPool(NITI)) {
			for (User student : studenti) {
				rezultati.add(niti.submit(() -> {
					start.await();
					try {
						service.join(student.getId(), token);
						return "UŠAO";
					} catch (InviteLinkNotUsableException e) {
						return e.getReason().name();
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
		// Svi ostali dobiju "pun", a ne grešku iz baze. Članova i korištenja mora biti isto: ko nije
		// prošao brojanje, ne smije ostati upisan kao član (rollback).
		assertThat(ishodi).filteredOn("UŠAO"::equals).hasSize(MJESTA);
		assertThat(ishodi).containsOnly("UŠAO", "FULL");
		assertThat(jdbc.queryForObject("select uses from invite_links where organization_id = ?", Integer.class,
				organizacija)).isEqualTo(MJESTA);
		assertThat(jdbc.queryForObject("select count(*) from organization_members where organization_id = ? "
				+ "and role = 'CLIENT'", Integer.class, organizacija)).isEqualTo(MJESTA);
	}

}
