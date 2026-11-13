package ba.slotbook.organization;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ba.slotbook.paging.PageResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

	private final OrganizationService organizationService;

	public OrganizationController(OrganizationService organizationService) {
		this.organizationService = organizationService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public OrganizationResponse create(@AuthenticationPrincipal Jwt token,
			@Valid @RequestBody CreateOrganizationRequest request) {
		return organizationService.create(UUID.fromString(token.getSubject()), request.name(), request.slug(),
				request.timezone());
	}

	@GetMapping("/{slug}")
	public OrganizationResponse getBySlug(@PathVariable String slug) {
		return organizationService.getBySlug(slug);
	}

	// Provjera se izvrši prije metode, s #id iz putanje. Ne-član i član bez uloge dobiju 403.
	@GetMapping("/{id}/members")
	@PreAuthorize("@organizationAccess.hasRole(#id, authentication, 'ADMIN')")
	public PageResponse<MemberResponse> listMembers(@PathVariable UUID id, Pageable pageable) {
		return organizationService.listMembers(id, pageable);
	}

}
