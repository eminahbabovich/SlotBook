package ba.slotbook.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthRateLimitInterceptorTest {

	// Sat koji test pomjera. Clock.fixed se ne može pomjeriti, a bucket se dopunjava samo kad vrijeme ide.
	private static final class Sat extends Clock {

		private Instant sada = Instant.parse("2035-11-19T08:00:00Z");

		void pomjeri(Duration koliko) {
			sada = sada.plus(koliko);
		}

		@Override
		public Instant instant() {
			return sada;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

	}

	private final Sat sat = new Sat();
	private final AuthRateLimitInterceptor granica = new AuthRateLimitInterceptor(sat, 3, 2, "");

	private boolean prijava(String adresa) {
		MockHttpServletRequest zahtjev = new MockHttpServletRequest("POST", "/api/auth/login");
		zahtjev.setRemoteAddr(adresa);
		return granica.preHandle(zahtjev, new MockHttpServletResponse(), new Object());
	}

	@Test
	void cetvrtiPokusajCekaDvadesetSekundiAPoslijeTogaJedanProlazi() {
		for (int i = 0; i < 3; i++) {
			assertThat(prijava("203.0.113.7")).isTrue();
		}

		// 3 u minuti: jedan pokušaj se vraća svakih 20 s
		assertThatThrownBy(() -> prijava("203.0.113.7"))
				.isInstanceOfSatisfying(TooManyAttemptsException.class,
						ex -> assertThat(ex.sekundiDoSljedeceg()).isEqualTo(20));

		sat.pomjeri(Duration.ofSeconds(20));
		assertThat(prijava("203.0.113.7")).isTrue();
		assertThatThrownBy(() -> prijava("203.0.113.7")).isInstanceOf(TooManyAttemptsException.class);
	}

	// Zaokružuje se naviše: "pokušaj za 0 s" bi klijenta odmah vratio na isti 429, a s 1 s umjesto 2
	// bi stigao pola sekunde prerano
	@Test
	void cekanjeSeZaokruzujeNavise() {
		for (int i = 0; i < 3; i++) {
			prijava("203.0.113.8");
		}
		sat.pomjeri(Duration.ofMillis(18_500));
		assertThatThrownBy(() -> prijava("203.0.113.8"))
				.isInstanceOfSatisfying(TooManyAttemptsException.class,
						ex -> assertThat(ex.sekundiDoSljedeceg()).isEqualTo(2));

		sat.pomjeri(Duration.ofMillis(1_000));
		assertThatThrownBy(() -> prijava("203.0.113.8"))
				.isInstanceOfSatisfying(TooManyAttemptsException.class,
						ex -> assertThat(ex.sekundiDoSljedeceg()).isEqualTo(1));
	}

	@Test
	void cekanjeOdJedneNanosekundeJeJednaSekunda() {
		assertThat(new TooManyAttemptsException(TooManyAttemptsException.Razlog.ADRESA, Duration.ofNanos(1)).sekundiDoSljedeceg()).isEqualTo(1);
	}

	@Test
	void ciscenjeBaciSamoPuneBucketeJerSuIstiKaoNovi() {
		prijava("203.0.113.1");
		sat.pomjeri(Duration.ofMinutes(1));
		prijava("203.0.113.2");

		granica.ocisti();

		// Prvi se za minutu dopunio, a drugom fali jedan pokušaj
		assertThat(granica.brojAdresa()).isEqualTo(1);
		for (int i = 0; i < 2; i++) {
			prijava("203.0.113.2");
		}
		assertThatThrownBy(() -> prijava("203.0.113.2")).isInstanceOf(TooManyAttemptsException.class);
	}

}
