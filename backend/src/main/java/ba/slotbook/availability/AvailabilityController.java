package ba.slotbook.availability;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
public class AvailabilityController {

	private final AvailabilityService availabilityService;

	public AvailabilityController(AvailabilityService availabilityService) {
		this.availabilityService = availabilityService;
	}

	@GetMapping("/api/resources/{resourceId}/availability")
	@PreAuthorize("@resourceAccess.canView(#resourceId, authentication)")
	public AvailabilityResponse get(@PathVariable UUID resourceId) {
		return availabilityService.get(resourceId);
	}

	// Radno vrijeme mijenja ko smije mijenjati resurs: admin, ili provider koji ga vodi
	@PutMapping("/api/resources/{resourceId}/availability")
	@PreAuthorize("@resourceAccess.canManage(#resourceId, authentication)")
	public AvailabilityResponse replace(@PathVariable UUID resourceId,
			@Valid @RequestBody AvailabilityRequest request) {
		return availabilityService.replace(resourceId, request.rules());
	}

}
