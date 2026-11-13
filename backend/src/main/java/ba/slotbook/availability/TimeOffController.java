package ba.slotbook.availability;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ba.slotbook.paging.PageResponse;
import jakarta.validation.Valid;

// Sve tri rute traže canManage, i čitanje: razlog (npr. bolovanje) nije za klijente.
// Klijent će u koraku 5 samo vidjeti da u tom periodu nema slobodnih termina.
@RestController
public class TimeOffController {

	private final TimeOffService timeOffService;

	public TimeOffController(TimeOffService timeOffService) {
		this.timeOffService = timeOffService;
	}

	@PostMapping("/api/resources/{resourceId}/time-off")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@resourceAccess.canManage(#resourceId, authentication)")
	public TimeOffResponse create(@PathVariable UUID resourceId, @Valid @RequestBody TimeOffRequest request) {
		return timeOffService.create(resourceId, request.startsAt(), request.endsAt(), request.reason());
	}

	@GetMapping("/api/resources/{resourceId}/time-off")
	@PreAuthorize("@resourceAccess.canManage(#resourceId, authentication)")
	public PageResponse<TimeOffResponse> list(@PathVariable UUID resourceId, Pageable pageable) {
		return timeOffService.listCurrent(resourceId, pageable);
	}

	@DeleteMapping("/api/resources/{resourceId}/time-off/{timeOffId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("@resourceAccess.canManage(#resourceId, authentication)")
	public void delete(@PathVariable UUID resourceId, @PathVariable UUID timeOffId) {
		timeOffService.delete(resourceId, timeOffId);
	}

}
