package ba.slotbook.chat;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ba.slotbook.ratelimit.TooManyAttemptsException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;

// Dnevna granica tokena ne čuva od skripte koja pošalje sto poruka u minuti. Tih sto poruka bi
// pojelo besplatnih 30 zahtjeva u minuti kod provajdera za sve korisnike. Zato i granica po minuti.
// Po korisniku, a ne po adresi, jer je ruta iza prijave.
@Component
class ChatRateLimit {

	private final Map<UUID, Bucket> korisnici = new ConcurrentHashMap<>();
	private final TimeMeter sat;
	private final int porukaUMinuti;

	ChatRateLimit(Clock clock, @Value("${slotbook.ai.messages-per-minute}") int porukaUMinuti) {
		// Isto kao u AuthRateLimitInterceptor: vrijeme iz Clock beana, da ga test može pomjeriti
		this.sat = new TimeMeter() {

			@Override
			public long currentTimeNanos() {
				return TimeUnit.MILLISECONDS.toNanos(clock.millis());
			}

			@Override
			public boolean isWallClockBased() {
				return true;
			}

		};
		this.porukaUMinuti = porukaUMinuti;
	}

	void potrosi(UUID korisnik) {
		Bucket bucket = korisnici.computeIfAbsent(korisnik, k -> Bucket.builder()
				.addLimit(limit -> limit.capacity(porukaUMinuti).refillGreedy(porukaUMinuti, Duration.ofMinutes(1)))
				.withCustomTimePrecision(sat)
				.build());
		ConsumptionProbe pokusaj = bucket.tryConsumeAndReturnRemaining(1);
		if (!pokusaj.isConsumed()) {
			throw new TooManyAttemptsException(TooManyAttemptsException.Razlog.PORUKE,
					Duration.ofNanos(pokusaj.getNanosToWaitForRefill()));
		}
	}

	@Scheduled(fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
	void ocisti() {
		korisnici.values().removeIf(bucket -> bucket.getAvailableTokens() >= porukaUMinuti);
	}

}
