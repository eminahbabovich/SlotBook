package ba.slotbook.booking;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
public class BookingController {

	private final BookingService bookingService;

	public BookingController(BookingService bookingService) {
		this.bookingService = bookingService;
	}

	// Rezerviše ko vidi resurs: član organizacije, a klijent samo na aktivnom resursu
	@PostMapping("/api/resources/{resourceId}/bookings")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@resourceAccess.canView(#resourceId, authentication)")
	public BookingResponse create(@PathVariable UUID resourceId, @AuthenticationPrincipal Jwt token,
			@Valid @RequestBody BookingRequest request,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
		// Ponovljen zahtjev s istim ključem vraća isti odgovor, i isti status 201
		return bookingService.create(resourceId, UUID.fromString(token.getSubject()),
				request.startTime().toInstant(), idempotencyKey);
	}

}
