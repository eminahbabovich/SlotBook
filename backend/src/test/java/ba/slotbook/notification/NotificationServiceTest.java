package ba.slotbook.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.user.User;
import ba.slotbook.user.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class NotificationServiceTest {

	@Autowired
	NotificationService service;

	@Autowired
	UserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	private UUID korisnik() {
		return users.save(new User("nedim." + UUID.randomUUID() + "@example.com", "hash", "Nedim",
				Instant.parse("2035-11-01T10:00:00Z"))).getId();
	}

	// Poziv iz koda koji nema transakciju je greška u kodu, a ne obavijest koja se upiše sama
	@Test
	void obavijestSeNeUpisujeBezTransakcijePozivaoca() {
		UUID nedim = korisnik();

		assertThatThrownBy(() -> service.enqueueForBooking(nedim, NotificationType.BOOKING_CONFIRMED, UUID.randomUUID()))
				.isInstanceOf(IllegalTransactionStateException.class);
		assertThat(jdbc.queryForObject("select count(*) from notifications where user_id = ?", Long.class, nedim))
				.isZero();
	}

	@Test
	void bazaOdbijaObavijestORezervacijiBezRezervacije() {
		UUID nedim = korisnik();

		assertThatThrownBy(() -> jdbc.update("insert into notifications (id, user_id, type, status, next_attempt_at, "
				+ "created_at) values (gen_random_uuid(), ?, 'BOOKING_CONFIRMED', 'PENDING', now(), now())", nedim))
				.hasMessageContaining("notifications_rezervacija");
	}

}
