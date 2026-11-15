package ba.slotbook.organization;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Bez @PreAuthorize: ruta nema ID organizacije, a upit gleda samo korisnika iz tokena.
// Prijavu traži SecurityConfig kao za svaki /api zahtjev.
@RestController
public class MyOrganizationsController {

	private final OrganizationService organizationService;

	public MyOrganizationsController(OrganizationService organizationService) {
		this.organizationService = organizationService;
	}

	@GetMapping("/api/me/organizations")
	public List<MyOrganizationResponse> mine(@AuthenticationPrincipal Jwt token) {
		return organizationService.listForUser(UUID.fromString(token.getSubject()));
	}

}
