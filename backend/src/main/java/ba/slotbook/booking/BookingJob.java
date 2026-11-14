package ba.slotbook.booking;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Pozadinski posao za rezervacije kojima je prošlo vrijeme. Ne mora biti tačan u sekundu: istekla
// rezervacija ne smeta novoj na istom resursu, jer je BookingService.create sam oslobodi.
@Component
class BookingJob {

	private static final Logger log = LoggerFactory.getLogger(BookingJob.class);

	private final BookingService bookingService;

	BookingJob(BookingService bookingService) {
		this.bookingService = bookingService;
	}

	// fixedDelay: sljedeće pokretanje kreće minutu poslije kraja prethodnog, pa se dva ne preklope
	// ni kad baza uspori. Izuzetak Spring zapiše u log, a posao se vrti dalje.
	@Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
	void obradi() {
		int istekle = bookingService.expireOverdue();
		int zatvorene = bookingService.completeEnded();
		if (istekle + zatvorene > 0) {
			log.info("Isteklo {} nepotvrđenih rezervacija, zatvoreno {} završenih", istekle, zatvorene);
		}
	}

}
