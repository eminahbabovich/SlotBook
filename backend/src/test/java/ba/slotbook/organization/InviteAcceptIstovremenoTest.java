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

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.user.User;
import ba.slotbook.user.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class InviteAcceptIstovremenoTest {

	// Manje od 10, koliko Hikari daje konekcija, da niti čekaju na zaključan red, a ne na konekciju
	private static final int NITI = 8;

	@Autowired
	OrganizationInviteService service;

	@Autowired
	OrganizationRepository organizations;

	@Autowired
	UserRepository users;

	@Test
	void istaPozivnicaPoslanaIstovremenoPrihvatiSeTacnoJednom() throws Exception {
		Instant sada = Instant.parse("2026-11-12T10:00:00Z");
		Organization sala = organizations.save(new Organization("Sala za sastanke ETF", "sala-istovremeno",
				"Europe/Sarajevo", sada));
		User amina = users.save(new User("amina.istovremeno@example.com", "hash", "Amina", sada));
		String token = service.invite(sala.getId(), amina.getEmail(), OrganizationRole.CLIENT).token();

		CountDownLatch start = new CountDownLatch(1);
		List<Future<String>> rezultati = new ArrayList<>();
		try (ExecutorService niti = Executors.newFixedThreadPool(NITI)) {
			for (int i = 0; i < NITI; i++) {
				rezultati.add(niti.submit(() -> {
					start.await();
					try {
						service.accept(amina.getId(), token);
						return "OK";
					} catch (InviteNotUsableException e) {
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
		// Ne samo "jedan OK": ostali moraju dobiti naše odbijanje, a ne grešku iz baze koja bi bila 500
		assertThat(ishodi).containsOnlyOnce("OK").containsOnly("OK", "ALREADY_USED");
	}

}
