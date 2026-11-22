package ba.slotbook.resource;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ba.slotbook.paging.PageResponse;
import jakarta.validation.Valid;

@RestController
public class ResourceController {

	private final ResourceService resourceService;

	public ResourceController(ResourceService resourceService) {
		this.resourceService = resourceService;
	}

	@PostMapping("/api/organizations/{id}/resources")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@organizationAccess.hasRole(#id, authentication, 'ADMIN', 'PROVIDER')")
	public ResourceResponse create(@PathVariable UUID id, @AuthenticationPrincipal Jwt token,
			@Valid @RequestBody ResourceRequest request) {
		return resourceService.create(id, UUID.fromString(token.getSubject()), request.name(),
				request.description(), request.slotMinutes(), request.autoConfirm(),
				request.cancelNoticeHours(), request.capacity(), request.priceCents());
	}

	// PUT mijenja sva polja koja se uređuju, pa tijelo ima ista pravila kao kod kreiranja
	@PutMapping("/api/organizations/{id}/resources/{resourceId}")
	@PreAuthorize("@resourceAccess.canManage(#resourceId, authentication)")
	public ResourceResponse update(@PathVariable UUID id, @PathVariable UUID resourceId,
			@AuthenticationPrincipal Jwt token, @Valid @RequestBody ResourceRequest request) {
		return resourceService.update(id, UUID.fromString(token.getSubject()), resourceId, request.name(), request.description(), request.slotMinutes(),
				request.autoConfirm(), request.cancelNoticeHours(), request.capacity(), request.priceCents());
	}

	// Ko vodi resurs određuje samo admin, provider ne može resurs prebaciti na drugoga
	@PutMapping("/api/organizations/{id}/resources/{resourceId}/provider")
	@PreAuthorize("@organizationAccess.hasRole(#id, authentication, 'ADMIN')")
	public ResourceResponse changeProvider(@PathVariable UUID id, @PathVariable UUID resourceId,
			@AuthenticationPrincipal Jwt token, @Valid @RequestBody ChangeProviderRequest request) {
		return resourceService.changeProvider(id, UUID.fromString(token.getSubject()), resourceId,
				request.providerId());
	}

	// DELETE ne briše red nego deaktivira resurs, vidi ResourceService.deactivate
	@DeleteMapping("/api/organizations/{id}/resources/{resourceId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("@resourceAccess.canManage(#resourceId, authentication)")
	public void deactivate(@PathVariable UUID id, @PathVariable UUID resourceId, @AuthenticationPrincipal Jwt token) {
		resourceService.deactivate(id, UUID.fromString(token.getSubject()), resourceId);
	}

	// Organizacije nema u putanji, pa canView do nje dolazi preko resursa
	@GetMapping("/api/resources/{resourceId}")
	@PreAuthorize("@resourceAccess.canView(#resourceId, authentication)")
	public ResourceResponse get(@PathVariable UUID resourceId) {
		return resourceService.get(resourceId);
	}

	// Klijent mora vidjeti resurse da bi izabrao šta rezerviše, pa listu vide sve tri uloge.
	// Pageable Spring sam napravi iz ?page=0&size=20.
	@GetMapping("/api/organizations/{id}/resources")
	@PreAuthorize("@organizationAccess.hasRole(#id, authentication, 'ADMIN', 'PROVIDER', 'CLIENT')")
	public PageResponse<ResourceResponse> list(@PathVariable UUID id, @AuthenticationPrincipal Jwt token,
			Pageable pageable) {
		return resourceService.list(id, UUID.fromString(token.getSubject()), pageable);
	}

}
