package ba.slotbook.auth;

import static org.assertj.core.api.Assertions.assertThat;

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
class LoginLockIstovremenoTest {

	// Ispod granice od 5, da se vidi tačan broj: izgubljeno brojanje bi dalo manje od 4
	private static final int NITI = 4;
	private static final int KRUGOVA = 15;

	@Autowired
	LoginLockService zakljucavanje;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void istovremenePogresneLozinkeSeSveIzbroje() throws Exception {
		List<String> ishodi = new ArrayList<>();
		for (int krug = 0; krug < KRUGOVA; krug++) {
			String email = "nedim.istovremeno" + krug + "@example.com";
			CountDownLatch start = new CountDownLatch(1);
			List<Future<String>> rezultati = new ArrayList<>();
			try (ExecutorService niti = Executors.newFixedThreadPool(NITI)) {
				for (int i = 0; i < NITI; i++) {
					rezultati.add(niti.submit(() -> {
						start.await();
						try {
							zakljucavanje.recordFailure(email);
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
			assertThat(jdbc.queryForObject("select failures from login_failures where email = ?", Integer.class,
					email)).as("krug %d", krug).isEqualTo(NITI);
		}
		// Prvi upis za isti email u isto vrijeme ne smije pasti na duplom ključu
		assertThat(ishodi).containsOnly("OK");
	}

}
