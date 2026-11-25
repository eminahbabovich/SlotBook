package ba.slotbook.demo;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Render na besplatnom planu uspava servis poslije 15 minuta bez zahtjeva, pa posao "svaku noć u 4"
// ne bi krenuo dok servis spava. Zato se provjera radi pri svakom pokretanju i svaki sat poslije.
// Odvojeno od DemoPodaci, jer poziv iz iste klase ne bi prošao kroz @Transactional.
@Component
@Profile("demo")
class DemoOsvjezavanje {

	private static final Logger log = LoggerFactory.getLogger(DemoOsvjezavanje.class);

	private final DemoPodaci podaci;

	DemoOsvjezavanje(DemoPodaci podaci) {
		this.podaci = podaci;
	}

	@EventListener(ApplicationReadyEvent.class)
	void naPokretanju() {
		osvjezi();
	}

	@Scheduled(initialDelay = 1, fixedDelay = 1, timeUnit = TimeUnit.HOURS)
	void svakiSat() {
		osvjezi();
	}

	private void osvjezi() {
		if (podaci.osvjeziAkoTreba()) {
			log.info("Demo podaci su vraćeni na početak");
		}
	}

}
