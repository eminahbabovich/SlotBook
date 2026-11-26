package ba.slotbook.organization;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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

@RestController
public class InviteController {

	private final OrganizationInviteService inviteService;

	public InviteController(OrganizationInviteService inviteService) {
		this.inviteService = inviteService;
	}

	@PostMapping("/api/organizations/{id}/invites")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@organizationAccess.hasRole(#id, authentication, 'ADMIN')")
	public InviteResponse invite(@PathVariable UUID id, @AuthenticationPrincipal Jwt token,
			@Valid @RequestBody CreateInviteRequest request) {
		return inviteService.invite(id, UUID.fromString(token.getSubject()), request.email(), request.role());
	}

	@GetMapping("/api/organizations/{id}/invites")
	@PreAuthorize("@organizationAccess.hasRole(#id, authentication, 'ADMIN')")
	public PageResponse<InviteResponse> listOpen(@PathVariable UUID id, Pageable pageable) {
		return inviteService.listOpen(id, pageable);
	}

	@DeleteMapping("/api/organizations/{id}/invites/{inviteId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("@organizationAccess.hasRole(#id, authentication, 'ADMIN')")
	public void revoke(@PathVariable UUID id, @PathVariable UUID inviteId, @AuthenticationPrincipal Jwt token) {
		inviteService.revoke(id, inviteId, UUID.fromString(token.getSubject()));
	}

	// Bez @PreAuthorize: osoba još nije član, a pravo daje sam token i email iz pozivnice
	@PostMapping("/api/invites/accept")
	public AcceptedInviteResponse accept(@AuthenticationPrincipal Jwt token,
			@Valid @RequestBody AcceptInviteRequest request) {
		return inviteService.accept(UUID.fromString(token.getSubject()), request.token());
	}

}
