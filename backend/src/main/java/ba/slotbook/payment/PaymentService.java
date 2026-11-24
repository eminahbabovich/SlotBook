package ba.slotbook.payment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.booking.Booking;
import ba.slotbook.booking.BookingService;

@Service
public class PaymentService {

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

}
