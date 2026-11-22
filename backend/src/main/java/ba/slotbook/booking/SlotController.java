package ba.slotbook.booking;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// U paketu booking, a ne availability: slobodan termin je ponuđen po pravilima i nije rezervisan,
// a availability ne zna za rezervacije. Tako booking zavisi od availability, a ne i obrnuto.
@RestController
public class SlotController {

	private final FreeSlotService freeSlotService;
	private final SlotStream slotStream;

	public SlotController(FreeSlotService freeSlotService, SlotStream slotStream) {
		this.freeSlotService = freeSlotService;
		this.slotStream = slotStream;
	}

	// Slobodne termine vidi svako ko vidi resurs, i klijent, jer po njima bira šta će rezervisati
	@GetMapping("/api/resources/{resourceId}/slots")
	@PreAuthorize("@resourceAccess.canView(#resourceId, authentication)")
	public SlotCalendarResponse slots(@PathVariable UUID resourceId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @AuthenticationPrincipal Jwt token) {
		return freeSlotService.calendar(resourceId, from, to, UUID.fromString(token.getSubject()));
	}

	// Isto pravo kao za /slots. Tok nosi samo signal "promijenilo se", a podatke kalendar čita kroz /slots.
	@GetMapping(path = "/api/resources/{resourceId}/slots/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@PreAuthorize("@resourceAccess.canView(#resourceId, authentication)")
	public SseEmitter stream(@PathVariable UUID resourceId) {
		return slotStream.open(resourceId);
	}

}
