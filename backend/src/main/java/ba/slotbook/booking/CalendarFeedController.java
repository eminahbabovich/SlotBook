package ba.slotbook.booking;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CalendarFeedController {

	private static final MediaType ICALENDAR = new MediaType("text", "calendar", StandardCharsets.UTF_8);

	private final CalendarFeedService calendarFeedService;

	public CalendarFeedController(CalendarFeedService calendarFeedService) {
		this.calendarFeedService = calendarFeedService;
	}

	// Korisnik iz tokena, nikad iz putanje, kao kod izvoza podataka
	@GetMapping("/api/me/calendar-feed")
	public CalendarFeedResponse status(@AuthenticationPrincipal Jwt token) {
		return calendarFeedService.status(UUID.fromString(token.getSubject()));
	}

	@PostMapping("/api/me/calendar-feed")
	@ResponseStatus(HttpStatus.CREATED)
	public CalendarFeedResponse replace(@AuthenticationPrincipal Jwt token) {
		return calendarFeedService.replace(UUID.fromString(token.getSubject()));
	}

	@DeleteMapping("/api/me/calendar-feed")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal Jwt token) {
		calendarFeedService.delete(UUID.fromString(token.getSubject()));
	}

	// Javno (SecurityConfig). required = false: bez tokena je isti 404 kao s pogrešnim, a ne 400.
	@GetMapping("/api/me/calendar.ics")
	public ResponseEntity<String> ics(@RequestParam(required = false) String token) {
		return ResponseEntity.ok().contentType(ICALENDAR).body(calendarFeedService.ics(token));
	}

}
