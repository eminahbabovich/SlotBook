package ba.slotbook.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import ba.slotbook.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class RegistracijaIstovremenoTest {

	// Manje od 10 konekcija koliko daje Hikari, da niti čekaju na bazu, a ne na konekciju
	private static final int NITI = 6;
	private static final int KRUGOVA = 10;
	private static final String LOZINKA = "trebevic-1984";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	// Sve niti vide da email nema nalog, sve upisuju, a UNIQUE pusti samo jednu. Ostale ne smiju dobiti
	// 409, jer bi se tako vidjelo da je nalog upravo nastao.
	@Test
	void istovremeneRegistracijeIstogEmailaSveDobiju202IJedanNalog() throws Exception {
		List<Integer> statusi = new ArrayList<>();
		for (int krug = 0; krug < KRUGOVA; krug++) {
			String email = "amina.istovremeno" + krug + "@example.com";
			CountDownLatch start = new CountDownLatch(1);
			List<Future<Integer>> odgovori = new ArrayList<>();
			try (ExecutorService niti = Executors.newFixedThreadPool(NITI)) {
				for (int i = 0; i < NITI; i++) {
					odgovori.add(niti.submit(() -> {
						start.await();
						return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
								.content("""
										{"email": "%s", "password": "%s", "displayName": "Amina"}
										""".formatted(email, LOZINKA)))
								.andReturn().getResponse().getStatus();
					}));
				}
				start.countDown();
				for (Future<Integer> odgovor : odgovori) {
					statusi.add(odgovor.get());
				}
			}
			assertThat(jdbc.queryForObject("select count(*) from users where email = ?", Long.class, email))
					.as("krug %d", krug).isEqualTo(1);
		}
		assertThat(statusi).containsOnly(202);
	}

}
