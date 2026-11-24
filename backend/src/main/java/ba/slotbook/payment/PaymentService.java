package ba.slotbook.payment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.booking.Booking;
import ba.slotbook.booking.BookingService;

@Service
public class PaymentService {

	private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

	static final String VALUTA = "bam";

	// Stripe ne prima sesiju kraću od 30 minuta. Minuta više, da sat našeg servera i Stripea ne
	// moraju biti tačno isti.
	static final Duration TRAJANJE_SESIJE = Duration.ofMinutes(31);

	private static final DateTimeFormatter TERMIN = DateTimeFormatter.ofPattern("d.M.yyyy. 'u' HH:mm");

	private final BookingService bookingService;
	private final PaymentRepository payments;
	private final StripeCheckout stripe;
	private final Clock clock;
	private final String appUrl;

	public PaymentService(BookingService bookingService, PaymentRepository payments, StripeCheckout stripe,
			Clock clock, @Value("${slotbook.app-url}") String appUrl) {
		this.bookingService = bookingService;
		this.payments = payments;
		this.stripe = stripe;
		this.clock = clock;
		this.appUrl = appUrl;
	}

	// Pristup (samo klijent čija je rezervacija) je provjerio @PreAuthorize. Stripe se zove dok je
	// rezervacija zaključana. To drži red do 20 s ako Stripe kasni, ali je onda redoslijed jasan: drugi
	// klik čeka prvi i dobije njegovu sesiju, a istek i otkazivanje ne mogu proći usred plaćanja.
	@Transactional
	public CheckoutResponse checkout(UUID bookingId) {
		Booking rezervacija = bookingService.lockForPayment(bookingId);
		Instant sada = clock.instant();
		// Istekla, a posao je još nije obradio, ne čeka uplatu iako je i dalje PENDING
		if (!rezervacija.cekaUplatu() || !rezervacija.getExpiresAt().isAfter(sada)) {
			throw new NotAwaitingPaymentException();
		}
		var otvorena = payments.findByBookingIdAndStatus(bookingId, PaymentStatus.OPEN);
		if (otvorena.isPresent()) {
			return CheckoutResponse.from(otvorena.get());
		}
		UUID id = UUID.randomUUID();
		var resurs = rezervacija.getResource();
		String termin = TERMIN.withZone(ZoneId.of(resurs.getOrganization().getTimezone()))
				.format(rezervacija.getStartTime());
		String povratak = appUrl + "/rezervacije?uplata=";
		StripeCheckout.Sesija sesija = stripe.otvori(new StripeCheckout.NovaSesija(id.toString(), bookingId,
				resurs.getName() + ", " + termin, rezervacija.getPriceCents(), VALUTA, sada.plus(TRAJANJE_SESIJE),
				povratak + "uspjeh", povratak + "odustao"));
		return CheckoutResponse.from(payments.save(new Payment(id, rezervacija, VALUTA, sesija, sada)));
	}

	// Webhook, potpis je već provjeren. Isti događaj može stići dvaput (Stripe ga ponovi kad naš odgovor
	// ne stigne do njega), a dva ista i u isto vrijeme. Zato uplata mijenja stanje samo iz OPEN i samo
	// pod lockom rezervacije: drugi isti događaj čeka prvi, nađe PAID i ne radi ništa.
	@Transactional
	public void obradi(StripeDogadjaj dogadjaj) {
		var bookingId = payments.findBookingIdBySession(dogadjaj.sessionId());
		if (bookingId.isEmpty()) {
			// Lokalni backend i Render dijele isti Stripe sandbox, pa stižu i tuđe sesije
			log.info("Stripe događaj {} za nepoznatu sesiju {}, preskačem", dogadjaj.id(), dogadjaj.sessionId());
			return;
		}
		// Prvo lock, pa tek onda čitanje uplate, da drugi isti događaj vidi ono što je prvi upisao
		Booking rezervacija = bookingService.lockForPayment(bookingId.get());
		Payment uplata = payments.findByStripeSessionId(dogadjaj.sessionId()).orElseThrow();
		if (uplata.getStatus() != PaymentStatus.OPEN) {
			return;
		}
		if (dogadjaj.tip().equals(StripeDogadjaj.ISTEKLO)) {
			uplata.istekla();
			return;
		}
		// Kartica je naplaćena odmah. Odgođene metode (npr. bankovni transfer) nismo uključili.
		if (!dogadjaj.placeno()) {
			return;
		}
		// Rezervacija koja još drži termin se potvrđuje i kad joj je rok prošao a posao je nije istekao:
		// termin je i dalje njen. Istekla ili otkazana ga više nema, pa novac ide nazad.
		if (rezervacija.cekaUplatu()) {
			uplata.placena(dogadjaj.paymentIntent());
			bookingService.confirmPaid(rezervacija);
		} else {
			stripe.vrati(dogadjaj.paymentIntent(), "povrat-" + uplata.getId());
			uplata.vracena(dogadjaj.paymentIntent());
			log.info("Uplata {} stigla za rezervaciju {} u stanju {}, novac vraćen", uplata.getId(),
					rezervacija.getId(), rezervacija.getStatus());
		}
	}

}
