package ba.slotbook.organization;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
public class InviteController {

	private final OrganizationInviteService inviteService;

	public InviteController(OrganizationInviteService inviteService) {
		this.inviteService = inviteService;
	}

	@PostMapping("/api/organizations/{id}/invites")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@organizationAccess.hasRole(#id, authentication, 'ADMIN')")
	public InviteResponse invite(@PathVariable UUID id, @Valid @RequestBody CreateInviteRequest request) {
		return inviteService.invite(id, request.email(), request.role());
	}

	// Bez @PreAuthorize: osoba još nije član, a pravo daje sam token i email iz pozivnice
	@PostMapping("/api/invites/accept")
	public AcceptedInviteResponse accept(@AuthenticationPrincipal Jwt token,
			@Valid @RequestBody AcceptInviteRequest request) {
		return inviteService.accept(UUID.fromString(token.getSubject()), request.token());
	}

}
