package ba.slotbook.booking;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ba.slotbook.availability.SlotsResponse;

// U paketu booking, a ne availability: slobodan termin je ponuđen po pravilima i nije rezervisan,
// a availability ne zna za rezervacije. Tako booking zavisi od availability, a ne i obrnuto.
@RestController
public class SlotController {

	private final FreeSlotService freeSlotService;

	public SlotController(FreeSlotService freeSlotService) {
		this.freeSlotService = freeSlotService;
	}

	// Slobodne termine vidi svako ko vidi resurs, i klijent, jer po njima bira šta će rezervisati
	@GetMapping("/api/resources/{resourceId}/slots")
	@PreAuthorize("@resourceAccess.canView(#resourceId, authentication)")
	public SlotsResponse slots(@PathVariable UUID resourceId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return freeSlotService.freeSlots(resourceId, from, to);
	}

}
