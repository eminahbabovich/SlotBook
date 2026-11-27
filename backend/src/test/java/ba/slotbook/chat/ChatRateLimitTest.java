package ba.slotbook.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import ba.slotbook.ratelimit.TooManyAttemptsException;

class ChatRateLimitTest {

	// Sat koji test pomjera. Clock.fixed se ne može pomjeriti, a bucket se dopunjava samo kad vrijeme ide.
	private static final class Sat extends Clock {

		private Instant sada = Instant.parse("2035-03-26T08:00:00Z");

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
	private final ChatRateLimit granica = new ChatRateLimit(sat, 3);
	private final UUID lejla = UUID.randomUUID();

	@Test
	void cetvrtaPorukaUMinutiJeOdbijenaSCekanjemDoSljedece() {
		granica.potrosi(lejla);
		granica.potrosi(lejla);
		granica.potrosi(lejla);

		// Tri u minuti je jedna svakih 20 s, pa četvrta čeka 20 s, a ne do kraja minute
		assertThatThrownBy(() -> granica.potrosi(lejla))
				.isInstanceOfSatisfying(TooManyAttemptsException.class, e -> {
					assertThat(e.getRazlog()).isEqualTo(TooManyAttemptsException.Razlog.PORUKE);
					assertThat(e.sekundiDoSljedeceg()).isEqualTo(20);
				});
	}

	@Test
	void poslijeCekanjaPorukaProlaziIDrugiKorisnikImaSvojuGranicu() {
		for (int i = 0; i < 3; i++) {
			granica.potrosi(lejla);
		}
		granica.potrosi(UUID.randomUUID());

		sat.pomjeri(Duration.ofSeconds(20));
		granica.potrosi(lejla);
	}

}
