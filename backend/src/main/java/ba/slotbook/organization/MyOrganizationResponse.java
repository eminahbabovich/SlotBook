package ba.slotbook.organization;

import java.util.UUID;

// Organizacija iz ugla prijavljenog korisnika: s njegovom ulogom, da frontend zna koje
// ekrane smije ponuditi.
public record MyOrganizationResponse(UUID id, String name, String slug, String timezone, OrganizationRole role) {

	static MyOrganizationResponse from(OrganizationMember member) {
		Organization organization = member.getOrganization();
		return new MyOrganizationResponse(organization.getId(), organization.getName(), organization.getSlug(),
				organization.getTimezone(), member.getRole());
	}

}
