package ba.slotbook.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class NotificationTest {

	private static final Instant NASTALA = Instant.parse("2035-11-19T08:00:00Z");

	private static Notification nova() {
		return new Notification(null, NotificationType.BOOKING_CONFIRMED, UUID.randomUUID(),
				new EmailContent("Rezervacija potvrđena", "Termin je tvoj."), NASTALA);
	}

	@Test
	void razmakIzmeduPokusajaRastePaSeOdustajePoslijePetog() {
		Notification obavijest = nova();
		List<Duration> razmaci = new ArrayList<>();
		Instant sada = NASTALA;
		for (int i = 0; i < 4; i++) {
			obavijest.neuspjela(sada, "MailSendException: Connection refused");
			razmaci.add(Duration.between(sada, obavijest.getNextAttemptAt()));
			sada = obavijest.getNextAttemptAt();
		}

		assertThat(razmaci).containsExactly(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(25),
				Duration.ofMinutes(125));
		assertThat(obavijest.getStatus()).isEqualTo(NotificationStatus.PENDING);

		obavijest.neuspjela(sada, "MailSendException: Connection refused");
		assertThat(obavijest.getStatus()).isEqualTo(NotificationStatus.FAILED);
		assertThat(obavijest.getAttempts()).isEqualTo(5);
	}

	@Test
	void dugaGreskaSeSkracujeNaVelicinuKolone() {
		Notification obavijest = nova();

		obavijest.neuspjela(NASTALA, "x".repeat(2000));

		assertThat(obavijest.getLastError()).hasSize(500);
	}

	@Test
	void poslanaPamtiKadJePoslanaIBriseStaruGresku() {
		Notification obavijest = nova();
		obavijest.neuspjela(NASTALA, "MailSendException: Connection refused");

		obavijest.poslana(NASTALA.plusSeconds(60));

		assertThat(obavijest.getStatus()).isEqualTo(NotificationStatus.SENT);
		assertThat(obavijest.getSentAt()).isEqualTo(NASTALA.plusSeconds(60));
		assertThat(obavijest.getLastError()).isNull();
		assertThat(obavijest.getAttempts()).isEqualTo(2);
	}

}
