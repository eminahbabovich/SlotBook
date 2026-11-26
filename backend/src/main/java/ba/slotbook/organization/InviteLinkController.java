package ba.slotbook.organization;

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
public class InviteLinkController {

	private final InviteLinkService inviteLinks;

	public InviteLinkController(InviteLinkService inviteLinks) {
		this.inviteLinks = inviteLinks;
	}

	@PostMapping("/api/organizations/{id}/invite-links")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@organizationAccess.hasRole(#id, authentication, 'ADMIN')")
	public InviteLinkResponse create(@PathVariable UUID id, @AuthenticationPrincipal Jwt token,
			@Valid @RequestBody CreateInviteLinkRequest request) {
		return inviteLinks.create(id, UUID.fromString(token.getSubject()), request.maxUses(), request.days());
	}

	@GetMapping("/api/organizations/{id}/invite-links")
	@PreAuthorize("@organizationAccess.hasRole(#id, authentication, 'ADMIN')")
	public PageResponse<InviteLinkResponse> list(@PathVariable UUID id, Pageable pageable) {
		return inviteLinks.list(id, pageable);
	}

	// Povlačenje, ne brisanje: link ostaje u listi, da admin vidi koliko je ljudi ušlo preko njega
	@DeleteMapping("/api/organizations/{id}/invite-links/{linkId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("@organizationAccess.hasRole(#id, authentication, 'ADMIN')")
	public void revoke(@PathVariable UUID id, @PathVariable UUID linkId, @AuthenticationPrincipal Jwt token) {
		inviteLinks.revoke(id, linkId, UUID.fromString(token.getSubject()));
	}

	// Javno: stranica /pridruzi pokaže ime organizacije i prije prijave. Pravo daje token.
	@GetMapping("/api/invite-links/{token}")
	public InviteLinkPreviewResponse preview(@PathVariable String token) {
		return inviteLinks.preview(token);
	}

	// Bez @PreAuthorize: osoba još nije član, pravo daje token, a prijava je obavezna (SecurityConfig)
	@PostMapping("/api/invite-links/join")
	public JoinedResponse join(@AuthenticationPrincipal Jwt token, @Valid @RequestBody JoinInviteLinkRequest request) {
		return inviteLinks.join(UUID.fromString(token.getSubject()), request.token());
	}

}
