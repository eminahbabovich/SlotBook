package ba.slotbook.organization;

import java.util.UUID;

// Organizacija iz ugla prijavljenog korisnika: s njegovom ulogom, da frontend zna koje
// ekrane smije ponuditi.
// noShowRules vide svi članovi: klijent treba znati pravilo prije nego što ga prekrši.
public record MyOrganizationResponse(UUID id, String name, String slug, String timezone, OrganizationRole role,
		NoShowRules noShowRules) {

	static MyOrganizationResponse from(OrganizationMember member) {
		Organization organization = member.getOrganization();
		return new MyOrganizationResponse(organization.getId(), organization.getName(), organization.getSlug(),
				organization.getTimezone(), member.getRole(), organization.getNedolasci());
	}

}
