package ba.slotbook.screen;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class DoorScreenController {

	private final DoorScreenService doorScreens;
	private final ScreenStream screenStream;

	public DoorScreenController(DoorScreenService doorScreens, ScreenStream screenStream) {
		this.doorScreens = doorScreens;
		this.screenStream = screenStream;
	}

	@PostMapping("/api/organizations/{id}/screens")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@organizationAccess.hasRole(#id, authentication, 'ADMIN')")
	public DoorScreenResponse create(@PathVariable UUID id, @AuthenticationPrincipal Jwt token,
			@RequestBody CreateDoorScreenRequest request) {
		return doorScreens.create(id, request.resourceId(), UUID.fromString(token.getSubject()));
	}

	@GetMapping("/api/organizations/{id}/screens")
	@PreAuthorize("@organizationAccess.hasRole(#id, authentication, 'ADMIN')")
	public List<DoorScreenResponse> list(@PathVariable UUID id) {
		return doorScreens.list(id);
	}

	@DeleteMapping("/api/organizations/{id}/screens/{screenId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("@organizationAccess.hasRole(#id, authentication, 'ADMIN')")
	public void revoke(@PathVariable UUID id, @PathVariable UUID screenId, @AuthenticationPrincipal Jwt token) {
		doorScreens.revoke(id, screenId, UUID.fromString(token.getSubject()));
	}

	// Javno: tablet na vratima nema nalog. Token je jedina provjera, a granica po IP adresi
	// (AuthRateLimitInterceptor) usporava pogađanje.
	@GetMapping("/api/screens/{token}")
	public ScreenView view(@PathVariable String token) {
		return doorScreens.view(token);
	}

	@GetMapping(path = "/api/screens/{token}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter stream(@PathVariable String token) {
		return screenStream.open(doorScreens.subscription(token));
	}

}
