package ba.slotbook.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import ba.slotbook.user.User;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

class NotificationSenderTest {

	private static final Instant SADA = Instant.parse("2035-11-19T08:00:00Z");

	private final NotificationRepository notifications = mock(NotificationRepository.class);
	private final JavaMailSender mailSender = mock(JavaMailSender.class);
	private final SimpleMeterRegistry metrike = new SimpleMeterRegistry();
	private final NotificationSender sender = new NotificationSender(notifications, mailSender,
			Clock.fixed(SADA, ZoneOffset.UTC), "SlotBook <noreply@slotbook.local>", metrike);

	@BeforeEach
	void praznaPoruka() {
		when(mailSender.createMimeMessage()).thenAnswer(poziv -> new MimeMessage((Session) null));
	}

	// SMTP ne odgovara: obavijest ostaje, s razlogom, i čeka minutu
	@Test
	void neuspjeloSlanjeSePonavljaZaMinutu() {
		User emina = mock(User.class);
		when(emina.getEmail()).thenReturn("emina@example.com");
		Notification obavijest = new Notification(emina, NotificationType.BOOKING_CONFIRMED, UUID.randomUUID(),
				new EmailContent("Rezervacija potvrđena", "Termin je tvoj."), SADA.minusSeconds(30));
		when(notifications.findNextDue(SADA)).thenReturn(Optional.of(obavijest));
		doThrow(new MailSendException("Connection refused")).when(mailSender).send(any(MimeMessage.class));

		assertThat(sender.sendNext()).isTrue();

		assertThat(obavijest.getStatus()).isEqualTo(NotificationStatus.PENDING);
		assertThat(obavijest.getAttempts()).isEqualTo(1);
		assertThat(obavijest.getNextAttemptAt()).isEqualTo(SADA.plusSeconds(60));
		assertThat(obavijest.getLastError()).isEqualTo("MailSendException: Connection refused");
		assertThat(metrike.counter("slotbook.mail.failed").count()).isEqualTo(1);
	}

	// Pozvana osoba možda još nema nalog, pa mail ide na adresu iz pozivnice
	@Test
	void pozivnicaIdeNaAdresuBezNaloga() throws Exception {
		Notification pozivnica = new Notification("tarik@example.com", UUID.randomUUID(),
				new EmailContent("Pozivnica na SlotBook: Teretana Grbavica", "link"), SADA);
		when(notifications.findNextDue(SADA)).thenReturn(Optional.of(pozivnica));

		sender.sendNext();

		ArgumentCaptor<MimeMessage> mail = ArgumentCaptor.forClass(MimeMessage.class);
		verify(mailSender).send(mail.capture());
		assertThat(mail.getValue().getRecipients(Message.RecipientType.TO)).extracting(Object::toString)
				.containsExactly("tarik@example.com");
		assertThat(pozivnica.getStatus()).isEqualTo(NotificationStatus.SENT);
	}

	@Test
	void bezObavijestiNemaSlanja() {
		when(notifications.findNextDue(SADA)).thenReturn(Optional.empty());

		assertThat(sender.sendNext()).isFalse();
	}

}
