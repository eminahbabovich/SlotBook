package ba.slotbook.payment;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

	private final PaymentService paymentService;

	public PaymentController(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	// 200, ne 201: klijent ne dobija resurs koji bi poslije čitao, nego adresu na koju ide. Drugi poziv
	// vraća istu adresu, pa je ponavljanje bezopasno.
	@PostMapping("/api/bookings/{bookingId}/pay")
	@PreAuthorize("@bookingAccess.isClient(#bookingId, authentication)")
	public CheckoutResponse pay(@PathVariable UUID bookingId) {
		return paymentService.checkout(bookingId);
	}

}
