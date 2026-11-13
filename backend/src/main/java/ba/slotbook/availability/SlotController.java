package ba.slotbook.availability;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SlotController {

	private final SlotService slotService;

	public SlotController(SlotService slotService) {
		this.slotService = slotService;
	}

	// Slobodne termine vidi svako ko vidi resurs, i klijent, jer po njima bira šta će rezervisati
	@GetMapping("/api/resources/{resourceId}/slots")
	@PreAuthorize("@resourceAccess.canView(#resourceId, authentication)")
	public SlotsResponse slots(@PathVariable UUID resourceId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return slotService.slots(resourceId, from, to);
	}

}
