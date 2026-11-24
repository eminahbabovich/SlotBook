package ba.slotbook.payment;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StripeWebhookController {

	private final StripeWebhookService webhook;

	public StripeWebhookController(StripeWebhookService webhook) {
		this.webhook = webhook;
	}

	// Tijelo kao String, ne kao objekat: potpis je nad bajtovima kako ih je Stripe poslao, a Jackson bi
	// ih pročitao i preuredio. Na sve osim 2xx Stripe šalje isti događaj ponovo (u live modu do tri dana).
	@PostMapping("/api/stripe/webhook")
	@ResponseStatus(HttpStatus.OK)
	public void primi(@RequestBody String tijelo,
			@RequestHeader(name = "Stripe-Signature", required = false) String potpis) {
		webhook.primi(tijelo, potpis);
	}

}
