package ba.slotbook.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.user.UserService;

// Na pravoj bazi, jer brojač je upsert u Postgresu. Svaki test ima svoj dan, da se ne broje tuđi tokeni.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ChatBudgetTest {

	@Autowired
	ChatRepository chat;

	@Autowired
	UserService userService;

	@Test
	void korisnikKojiPotrosiSvojDioDobijaGranicuADrugiNe() {
		var budzet = budzet("2035-03-02T09:00:00Z", 1000, 100_000);
		UUID emina = korisnik();
		UUID tarik = korisnik();

		budzet.zapisi(emina, 600);
		assertThatCode(() -> budzet.provjeri(emina)).doesNotThrowAnyException();
		budzet.zapisi(emina, 400);

		assertThatThrownBy(() -> budzet.provjeri(emina)).isInstanceOfSatisfying(ChatDailyLimitException.class,
				e -> assertThat(e.zaCijeluAplikaciju()).isFalse());
		assertThatCode(() -> budzet.provjeri(tarik)).doesNotThrowAnyException();
	}

	@Test
	void kadSviZajednoPotroseDnevnuKvotuNiNoviKorisnikNeProlazi() {
		var budzet = budzet("2035-03-03T09:00:00Z", 10_000, 1500);
		budzet.zapisi(korisnik(), 1000);
		budzet.zapisi(korisnik(), 500);

		assertThatThrownBy(() -> budzet.provjeri(korisnik())).isInstanceOfSatisfying(ChatDailyLimitException.class,
				e -> assertThat(e.zaCijeluAplikaciju()).isTrue());
	}

	@Test
	void granicaTrajeDoPonociPoUtcIPoslijeKreceOdNule() {
		UUID emina = korisnik();
		var uvece = budzet("2035-03-04T23:59:30Z", 1000, 100_000);
		uvece.zapisi(emina, 1000);

		assertThatThrownBy(() -> uvece.provjeri(emina)).isInstanceOfSatisfying(ChatDailyLimitException.class,
				e -> assertThat(e.sekundiDoPonoci()).isEqualTo(30));
		assertThatCode(() -> budzet("2035-03-05T00:00:10Z", 1000, 100_000).provjeri(emina))
				.doesNotThrowAnyException();
	}

	// Brojač je jedan INSERT ... ON CONFLICT, pa ga baza sabira atomarno. Čitanje pa upis iz Jave bi
	// izgubio dio tokena kad dvije poruke stignu u istom trenutku.
	@Test
	void dvadesetIstovremenihZapisaSabereSvakiToken() throws Exception {
		var budzet = budzet("2035-03-06T09:00:00Z", 1_000_000, 1_000_000);
		UUID emina = korisnik();
		CountDownLatch start = new CountDownLatch(1);
		List<Future<?>> rezultati = new ArrayList<>();
		try (ExecutorService izvrsilac = Executors.newFixedThreadPool(20)) {
			for (int i = 0; i < 20; i++) {
				rezultati.add(izvrsilac.submit(() -> {
					start.await();
					budzet.zapisi(emina, 100);
					return null;
				}));
			}
			start.countDown();
			for (Future<?> rezultat : rezultati) {
				rezultat.get();
			}
		}

		assertThat(chat.tokeniKorisnika(emina, LocalDate.parse("2035-03-06"))).isEqualTo(2000);
	}

	private ChatBudget budzet(String sada, long poKorisniku, long ukupno) {
		return new ChatBudget(chat, Clock.fixed(Instant.parse(sada), ZoneOffset.UTC), poKorisniku, ukupno);
	}

	private UUID korisnik() {
		String ime = UUID.randomUUID().toString().substring(0, 8);
		return userService.register(ime + ".budzet@example.com", "dnevna-granica-tokena-9", "Emina Halilović").id();
	}

}
