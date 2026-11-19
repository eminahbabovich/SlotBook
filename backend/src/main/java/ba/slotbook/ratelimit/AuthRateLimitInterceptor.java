package ba.slotbook.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Granica pokušaja prijave i registracije po IP adresi (token bucket). Bucketi su u memoriji, jer radi
// jedna instanca. Za više instanci bi trebali biti u Redisu, da svaka ne broji za sebe.
@Component
public class AuthRateLimitInterceptor implements HandlerInterceptor {

	private final Map<String, Bucket> prijave = new ConcurrentHashMap<>();
	private final Map<String, Bucket> registracije = new ConcurrentHashMap<>();
	private final TimeMeter sat;
	private final int prijavaUMinuti;
	private final int registracijaNaSat;
	private final String zaglavljeAdrese;

	public AuthRateLimitInterceptor(Clock clock,
			@Value("${slotbook.rate-limit.login-per-minute}") int prijavaUMinuti,
			@Value("${slotbook.rate-limit.register-per-hour}") int registracijaNaSat,
			@Value("${slotbook.rate-limit.client-ip-header:}") String zaglavljeAdrese) {
		// Bucket4j sam čita System.nanoTime. Ovako vrijeme ide kroz Clock, pa ga test može pomjeriti.
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
		this.prijavaUMinuti = prijavaUMinuti;
		this.registracijaNaSat = registracijaNaSat;
		this.zaglavljeAdrese = zaglavljeAdrese;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		Bucket bucket = request.getRequestURI().equals("/api/auth/login")
				? prijave.computeIfAbsent(adresa(request), a -> novi(prijavaUMinuti, Duration.ofMinutes(1)))
				: registracije.computeIfAbsent(adresa(request), a -> novi(registracijaNaSat, Duration.ofHours(1)));
		ConsumptionProbe pokusaj = bucket.tryConsumeAndReturnRemaining(1);
		if (!pokusaj.isConsumed()) {
			throw new TooManyAttemptsException(Duration.ofNanos(pokusaj.getNanosToWaitForRefill()));
		}
		return true;
	}

	// Greedy: pokušaji se vraćaju jedan po jedan kroz period (20 u minuti je jedan svake 3 s), a ne svi
	// odjednom na kraju minute, pa nema trenutka kad napadač dobije duplo
	private Bucket novi(int pokusaja, Duration period) {
		return Bucket.builder()
				.addLimit(limit -> limit.capacity(pokusaja).refillGreedy(pokusaja, period))
				.withCustomTimePrecision(sat)
				.build();
	}

	private String adresa(HttpServletRequest request) {
		if (!zaglavljeAdrese.isBlank()) {
			String izZaglavlja = request.getHeader(zaglavljeAdrese);
			if (izZaglavlja != null && !izZaglavlja.isBlank()) {
				return izZaglavlja.strip();
			}
		}
		return request.getRemoteAddr();
	}

	// Pun bucket je isti kao nov, pa se može baciti bez gubitka. Bez ovoga bi mapa rasla sa svakom
	// adresom koja se ikad pojavila.
	@Scheduled(fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
	void ocisti() {
		prijave.values().removeIf(bucket -> bucket.getAvailableTokens() >= prijavaUMinuti);
		registracije.values().removeIf(bucket -> bucket.getAvailableTokens() >= registracijaNaSat);
	}

	int brojAdresa() {
		return prijave.size() + registracije.size();
	}

}
