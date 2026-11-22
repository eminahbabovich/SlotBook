package ba.slotbook.booking;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ba.slotbook.paging.PageResponse;
import jakarta.validation.Valid;

@RestController
public class WaitlistController {

	private final WaitlistService waitlistService;

	public WaitlistController(WaitlistService waitlistService) {
		this.waitlistService = waitlistService;
	}

	// Na listu se upisuje ko smije rezervisati, a tijelo je isto kao kod rezervacije: samo početak
	@PostMapping("/api/resources/{resourceId}/waitlist")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@resourceAccess.canView(#resourceId, authentication)")
	public WaitlistEntryResponse join(@PathVariable UUID resourceId, @AuthenticationPrincipal Jwt token,
			@Valid @RequestBody BookingRequest request) {
		return waitlistService.join(resourceId, UUID.fromString(token.getSubject()),
				request.startTime().toInstant());
	}

	@GetMapping("/api/waitlist/mine")
	public PageResponse<MyWaitlistEntryResponse> mine(@AuthenticationPrincipal Jwt token, Pageable pageable) {
		return waitlistService.mine(UUID.fromString(token.getSubject()), pageable);
	}

	@DeleteMapping("/api/waitlist/{waitlistId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("@waitlistAccess.isOwner(#waitlistId, authentication)")
	public void leave(@PathVariable UUID waitlistId) {
		waitlistService.leave(waitlistId);
	}

}
