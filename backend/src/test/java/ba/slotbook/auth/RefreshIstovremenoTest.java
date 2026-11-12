package ba.slotbook.auth;

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
class RefreshIstovremenoTest {

	// Manje od 10, koliko Hikari daje konekcija, da niti čekaju na zaključan red, a ne na konekciju
	private static final int NITI = 8;

	@Autowired
	RefreshTokenService service;

	@Autowired
	UserRepository users;

	@Test
	void istiTokenPoslanIstovremenoRotiraSeTacnoJednom() throws Exception {
		User user = users.save(new User("istovremeno@example.com", "hash", "Kenan", Instant.now()));
		String token = service.issueNewFamily(user.getId());

		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> rezultati = new ArrayList<>();
		try (ExecutorService niti = Executors.newFixedThreadPool(NITI)) {
			for (int i = 0; i < NITI; i++) {
				rezultati.add(niti.submit(() -> {
					start.await();
					try {
						service.rotate(token);
						return true;
					} catch (InvalidRefreshTokenException e) {
						return false;
					}
				}));
			}
			start.countDown();
		}

		long uspjelo = 0;
		for (Future<Boolean> rezultat : rezultati) {
			if (rezultat.get()) {
				uspjelo++;
			}
		}
		assertThat(uspjelo).isEqualTo(1);
	}

}
