package ba.slotbook.notification;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

// Sve je pod /api/me: korisnik upravlja samo browserima prijavljenim za svoje obavijesti
@RestController
public class PushController {

	private final PushSubscriptionService service;

	public PushController(PushSubscriptionService service) {
		this.service = service;
	}

	@GetMapping("/api/push/public-key")
	public PushKeyResponse publicKey() {
		return service.publicKey();
	}

	// PUT, jer je endpoint prirodni ključ: isti browser prijavljen dvaput je i dalje jedna pretplata
	@PutMapping("/api/me/push-subscriptions")
	public PushSubscriptionResponse save(@AuthenticationPrincipal Jwt token,
			@Valid @RequestBody PushSubscriptionRequest zahtjev) {
		return service.save(UUID.fromString(token.getSubject()), zahtjev);
	}

	@DeleteMapping("/api/me/push-subscriptions")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal Jwt token, @RequestParam String endpoint) {
		service.delete(UUID.fromString(token.getSubject()), endpoint);
	}

	@PostMapping("/api/me/push-subscriptions/test")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void test(@AuthenticationPrincipal Jwt token, @Valid @RequestBody PushEndpointRequest zahtjev) {
		service.sendTest(UUID.fromString(token.getSubject()), zahtjev.endpoint());
	}

}
