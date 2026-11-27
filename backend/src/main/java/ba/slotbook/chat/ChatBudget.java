package ba.slotbook.chat;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Besplatnu dnevnu kvotu provajdera dijele svi korisnici. Granica po korisniku čuva da jedan ne potroši
// sve, a granica za cijelu aplikaciju da mi ne udarimo u kvotu provajdera usred nečijeg razgovora.
// Provjera je prije poruke, a tokeni se znaju tek poslije, pa zadnja poruka dana smije malo prebaciti.
@Component
class ChatBudget {

	private final ChatRepository chat;
	private final Clock clock;
	private final long poKorisniku;
	private final long ukupno;

	ChatBudget(ChatRepository chat, Clock clock, @Value("${slotbook.ai.daily-tokens-per-user}") long poKorisniku,
			@Value("${slotbook.ai.daily-tokens-total}") long ukupno) {
		this.chat = chat;
		this.clock = clock;
		this.poKorisniku = poKorisniku;
		this.ukupno = ukupno;
	}

	void provjeri(UUID korisnik) {
		LocalDate danas = danas();
		if (chat.tokeniSvih(danas) >= ukupno) {
			throw new ChatDailyLimitException(sekundiDoPonoci(), true);
		}
		if (chat.tokeniKorisnika(korisnik, danas) >= poKorisniku) {
			throw new ChatDailyLimitException(sekundiDoPonoci(), false);
		}
	}

	void zapisi(UUID korisnik, long tokeni) {
		chat.dodajTokene(korisnik, danas(), tokeni);
	}

	private LocalDate danas() {
		return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
	}

	private long sekundiDoPonoci() {
		return Duration.between(clock.instant(), danas().plusDays(1).atStartOfDay(ZoneOffset.UTC)).toSeconds();
	}

}
