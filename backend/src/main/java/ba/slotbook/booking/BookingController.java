package ba.slotbook.booking;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ba.slotbook.paging.PageResponse;
import jakarta.validation.Valid;

@RestController
public class BookingController {

	private final BookingService bookingService;
	private final BookingAccess bookingAccess;

	public BookingController(BookingService bookingService, BookingAccess bookingAccess) {
		this.bookingService = bookingService;
		this.bookingAccess = bookingAccess;
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

	// Samo svoje: ID klijenta je iz tokena, pa nema šta provjeravati osim prijave
	@GetMapping("/api/bookings/mine")
	public PageResponse<MyBookingResponse> mine(@AuthenticationPrincipal Jwt token, Pageable pageable) {
		return bookingService.mine(UUID.fromString(token.getSubject()), pageable);
	}

	@GetMapping("/api/bookings/{bookingId}/events")
	@PreAuthorize("@bookingAccess.canView(#bookingId, authentication)")
	public List<BookingEventResponse> history(@PathVariable UUID bookingId) {
		return bookingService.history(bookingId);
	}

	// Prelazi stanja su posebne adrese, a ne PUT sa statusom u tijelu: svaki ima svoje pravilo ko ga smije
	@PostMapping("/api/bookings/{bookingId}/confirm")
	@PreAuthorize("@bookingAccess.canManage(#bookingId, authentication)")
	public BookingResponse confirm(@PathVariable UUID bookingId, @AuthenticationPrincipal Jwt token) {
		return bookingService.changeStatus(bookingId, UUID.fromString(token.getSubject()), BookingStatus.CONFIRMED);
	}

	@PostMapping("/api/bookings/{bookingId}/reject")
	@PreAuthorize("@bookingAccess.canManage(#bookingId, authentication)")
	public BookingResponse reject(@PathVariable UUID bookingId, @AuthenticationPrincipal Jwt token) {
		return bookingService.changeStatus(bookingId, UUID.fromString(token.getSubject()), BookingStatus.REJECTED);
	}

	@PostMapping("/api/bookings/{bookingId}/cancel")
	@PreAuthorize("@bookingAccess.canView(#bookingId, authentication)")
	public BookingResponse cancel(@PathVariable UUID bookingId, @AuthenticationPrincipal Jwt token,
			Authentication authentication) {
		return bookingService.cancel(bookingId, UUID.fromString(token.getSubject()),
				bookingAccess.canManage(bookingId, authentication));
	}

	// Klijent je došao. QR check-in iz Faze 10 će ići na isti prelaz.
	@PostMapping("/api/bookings/{bookingId}/check-in")
	@PreAuthorize("@bookingAccess.canManage(#bookingId, authentication)")
	public BookingResponse checkIn(@PathVariable UUID bookingId, @AuthenticationPrincipal Jwt token) {
		return bookingService.changeStatus(bookingId, UUID.fromString(token.getSubject()), BookingStatus.COMPLETED);
	}

	@PostMapping("/api/bookings/{bookingId}/no-show")
	@PreAuthorize("@bookingAccess.canManage(#bookingId, authentication)")
	public BookingResponse noShow(@PathVariable UUID bookingId, @AuthenticationPrincipal Jwt token) {
		return bookingService.changeStatus(bookingId, UUID.fromString(token.getSubject()), BookingStatus.NO_SHOW);
	}

}
