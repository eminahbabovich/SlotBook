package ba.slotbook.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
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

	@Autowired
	ApplicationContext kontekst;

	// Inače bi svaki test koji napravi rezervaciju slao mail na localhost:1025
	@Test
	void testoviNeSaljuMailoveSami() {
		assertThat(kontekst.getBeansOfType(NotificationJob.class)).isEmpty();
	}

	private UUID korisnik() {
		return users.save(new User("nedim." + UUID.randomUUID() + "@example.com", "hash", "Nedim",
				Instant.parse("2035-11-01T10:00:00Z"))).getId();
	}

	// Poziv iz koda koji nema transakciju je greška u kodu, a ne obavijest koja se upiše sama
	@Test
	void obavijestSeNeUpisujeBezTransakcijePozivaoca() {
		UUID nedim = korisnik();

		assertThatThrownBy(() -> service.enqueueForBooking(nedim, NotificationType.BOOKING_CONFIRMED,
				UUID.randomUUID(), new EmailContent("Rezervacija potvrđena", "Termin je tvoj.")))
				.isInstanceOf(IllegalTransactionStateException.class);
		assertThat(jdbc.queryForObject("select count(*) from notifications where user_id = ?", Long.class, nedim))
				.isZero();
	}

	@Test
	void bazaOdbijaObavijestORezervacijiBezRezervacije() {
		UUID nedim = korisnik();

		assertThatThrownBy(() -> jdbc.update("insert into notifications (id, user_id, type, status, next_attempt_at, "
				+ "created_at, subject, body) values (gen_random_uuid(), ?, 'BOOKING_CONFIRMED', 'PENDING', now(), now(), "
				+ "'Rezervacija potvrđena', 'Termin je tvoj.')", nedim))
				.hasMessageContaining("notifications_rezervacija");
	}

	// Pozivnica ide na adresu, a sve ostalo korisniku. Nikad oboje, nikad nijedno.
	@Test
	void bazaTraziTacnoJednogPrimaoca() {
		UUID nedim = korisnik();

		assertThatThrownBy(() -> jdbc.update("insert into notifications (id, user_id, email, type, status, "
				+ "next_attempt_at, created_at, subject, body) values (gen_random_uuid(), ?, 'nedim@example.com', "
				+ "'ORGANIZATION_INVITE', 'PENDING', now(), now(), 'Pozivnica', 'link')", nedim))
				.hasMessageContaining("notifications_primalac");
		assertThatThrownBy(() -> jdbc.update("insert into notifications (id, type, status, next_attempt_at, "
				+ "created_at, subject, body) values (gen_random_uuid(), 'PASSWORD_RESET', 'PENDING', now(), now(), "
				+ "'Nova lozinka', 'link')"))
				.hasMessageContaining("notifications_primalac");
	}

	// Mail za potvrdu emaila nosi token. Poslana obavijest ga ne smije čuvati.
	@Test
	void bazaOdbijaPoslanuObavijestSTijelom() {
		UUID nedim = korisnik();

		assertThatThrownBy(() -> jdbc.update("insert into notifications (id, user_id, type, status, next_attempt_at, "
				+ "created_at, sent_at, subject, body) values (gen_random_uuid(), ?, 'EMAIL_VERIFICATION', 'SENT', "
				+ "now(), now(), now(), 'Potvrdi email za SlotBook', 'link s tokenom')", nedim))
				.hasMessageContaining("notifications_tijelo_dok_ceka");
	}

}
