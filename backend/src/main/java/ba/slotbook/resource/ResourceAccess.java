package ba.slotbook.resource;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import ba.slotbook.organization.OrganizationAccess;
import ba.slotbook.organization.OrganizationRole;

// Poziva se iz @PreAuthorize kao @resourceAccess.canManage(...). Organizacija se uzima iz resursa,
// ne iz putanje, pa admin jedne organizacije ne prođe s tuđim resursom ni kroz svoju putanju.
@Component
public class ResourceAccess {

	private final ResourceRepository resources;
	private final OrganizationAccess organizationAccess;

	public ResourceAccess(ResourceRepository resources, OrganizationAccess organizationAccess) {
		this.resources = resources;
		this.organizationAccess = organizationAccess;
	}

	// Admin uređuje svaki resurs organizacije, provider samo one koje vodi. Uloga se i ovdje čita
	// iz baze, pa provider koji je postao klijent više ne uređuje ni svoje stare resurse.
	public boolean canManage(UUID resourceId, Authentication authentication) {
		if (!(authentication instanceof JwtAuthenticationToken jwt)) {
			return false;
		}
		UUID korisnikId = UUID.fromString(jwt.getToken().getSubject());

		return resources.findById(resourceId)
				.map(resurs -> {
					UUID organizacijaId = resurs.getOrganization().getId();
					return organizationAccess.hasRole(organizacijaId, authentication, OrganizationRole.ADMIN)
							|| (resurs.getProvider().getId().equals(korisnikId) && organizationAccess
									.hasRole(organizacijaId, authentication, OrganizationRole.PROVIDER));
				})
				.orElse(false);
	}

	// Za rute /api/resources/{id}, gdje organizacije nema u putanji. Vidi ga svaki član organizacije
	// resursa, a klijent samo aktivan, kao u listi.
	public boolean canView(UUID resourceId, Authentication authentication) {
		return resources.findById(resourceId)
				.map(resurs -> {
					UUID organizacijaId = resurs.getOrganization().getId();
					return organizationAccess.hasRole(organizacijaId, authentication, OrganizationRole.ADMIN,
							OrganizationRole.PROVIDER)
							|| (resurs.isActive() && organizationAccess.hasRole(organizacijaId, authentication,
									OrganizationRole.CLIENT));
				})
				.orElse(false);
	}

}
