package ba.slotbook.notification;

import java.io.IOException;
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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
class NotificationSender {

	private static final Logger log = LoggerFactory.getLogger(NotificationSender.class);

	private final NotificationRepository notifications;
	private final PushSubscriptionRepository pretplate;
	private final WebPushClient webPush;
	private final JavaMailSender mailSender;
	private final Clock clock;
	private final String from;
	private final Counter neuspjeli;
	private final Counter neuspjeliPush;

	NotificationSender(NotificationRepository notifications, PushSubscriptionRepository pretplate,
			WebPushClient webPush, JavaMailSender mailSender, Clock clock, @Value("${slotbook.mail.from}") String from,
			MeterRegistry metrike) {
		this.notifications = notifications;
		this.pretplate = pretplate;
		this.webPush = webPush;
		this.mailSender = mailSender;
		this.clock = clock;
		this.from = from;
		// Svaki neuspio pokušaj, ne samo konačno FAILED: rast ovog broja je prvi znak da SMTP ne radi
		this.neuspjeli = Counter.builder("slotbook.mail.failed")
				.description("Pokušaji slanja maila koje je SMTP odbio").register(metrike);
		this.neuspjeliPush = Counter.builder("slotbook.push.failed")
				.description("Pokušaji slanja push obavijesti koje push servis nije primio").register(metrike);
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
		if (obavijest.isPush()) {
			posaljiPush(obavijest, sada);
			return true;
		}
		MimeMessage mail = sastavi(obavijest);
		try {
			mailSender.send(mail);
			obavijest.poslana(sada);
		} catch (MailException e) {
			// Samo greške slanja. Druga greška je bug i ide u log posla, a obavijest čeka popravku.
			obavijest.neuspjela(sada, e.getClass().getSimpleName() + ": " + e.getMessage());
			neuspjeli.increment();
			log.warn("Obavijest {} nije poslana, {}. pokušaj: {}", obavijest.getId(), obavijest.getAttempts(),
					e.getMessage());
		}
		return true;
	}

	// Isti ponovni pokušaji kao za mail. 404 i 410 znače da browser više nema pretplatu (odjavljen,
	// obrisani podaci sajta, istekla), pa nema smisla pokušavati ponovo: pretplata se briše.
	private void posaljiPush(Notification obavijest, Instant sada) {
		if (!webPush.ukljucen()) {
			obavijest.neuspjela(sada, "Push je isključen: nema VAPID ključeva");
			return;
		}
		PushSubscription pretplata = obavijest.getPushSubscription();
		try {
			int status = webPush.posalji(pretplata, obavijest.getBody());
			if (status == 404 || status == 410) {
				log.info("Push pretplata {} više ne postoji ({}), briše se", pretplata.getId(), status);
				pretplate.ukloni(pretplata.getId());
			} else if (status >= 200 && status < 300) {
				obavijest.poslana(sada);
			} else {
				obavijest.neuspjela(sada, "Push servis je vratio " + status);
				neuspjeliPush.increment();
			}
		} catch (IOException e) {
			obavijest.neuspjela(sada, e.getClass().getSimpleName() + ": " + e.getMessage());
			neuspjeliPush.increment();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			obavijest.neuspjela(sada, "Slanje prekinuto");
		}
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
