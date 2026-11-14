package ba.slotbook.notification;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
class NotificationSender {

	private static final Logger log = LoggerFactory.getLogger(NotificationSender.class);

	private final NotificationRepository notifications;
	private final JavaMailSender mailSender;
	private final Clock clock;
	private final String from;

	NotificationSender(NotificationRepository notifications, JavaMailSender mailSender, Clock clock,
			@Value("${slotbook.mail.from}") String from) {
		this.notifications = notifications;
		this.mailSender = mailSender;
		this.clock = clock;
		this.from = from;
	}

	// Jedna obavijest, jedna transakcija. Ako mail ode, a commit padne, obavijest ostane PENDING i
	// ode još jednom. Radije dvaput nego nijednom. Vraća false kad nema šta slati.
	@Transactional
	public boolean sendNext() {
		Instant sada = clock.instant();
		Optional<Notification> sljedeca = notifications.findNextDue(sada);
		if (sljedeca.isEmpty()) {
			return false;
		}
		Notification obavijest = sljedeca.get();
		MimeMessage mail = sastavi(obavijest);
		try {
			mailSender.send(mail);
			obavijest.poslana(sada);
		} catch (MailException e) {
			// Samo greške slanja. Druga greška je bug i ide u log posla, a obavijest čeka popravku.
			obavijest.neuspjela(sada, e.getClass().getSimpleName() + ": " + e.getMessage());
			log.warn("Obavijest {} nije poslana, {}. pokušaj: {}", obavijest.getId(), obavijest.getAttempts(),
					e.getMessage());
		}
		return true;
	}

	// MimeMessage, a ne SimpleMailMessage, jer potvrda nosi prilog. Greška pri sastavljanju nije
	// greška SMTP-a, nego bug (npr. neispravna adresa pošiljaoca), pa ide dalje kao izuzetak.
	private MimeMessage sastavi(Notification obavijest) {
		MimeMessage mail = mailSender.createMimeMessage();
		try {
			MimeMessageHelper poruka = new MimeMessageHelper(mail, obavijest.getIcs() != null, "UTF-8");
			poruka.setFrom(from);
			poruka.setTo(obavijest.getRecipient());
			poruka.setSubject(obavijest.getSubject());
			poruka.setText(obavijest.getBody());
			if (obavijest.getIcs() != null) {
				poruka.addAttachment("termin.ics", new ByteArrayResource(obavijest.getIcs().getBytes(StandardCharsets.UTF_8)),
						"text/calendar; charset=UTF-8; method=PUBLISH");
			}
		} catch (MessagingException e) {
			throw new IllegalStateException("Mail za obavijest " + obavijest.getId() + " se ne može sastaviti", e);
		}
		return mail;
	}

}
