package ba.slotbook.organization;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

// Poziva se iz @PreAuthorize kao @organizationAccess.hasRole(...)
@Component
public class OrganizationAccess {

	private final OrganizationMemberRepository members;

	public OrganizationAccess(OrganizationMemberRepository members) {
		this.members = members;
	}

	// Uloge nisu u tokenu, pa se čitaju iz baze kod svakog zahtjeva. Oduzeta uloga važi odmah,
	// a ne tek kad istekne access token.
	public boolean hasRole(UUID organizationId, Authentication authentication, OrganizationRole... uloge) {
		// Anonimni korisnik je isto "authenticated" u Springu, ali nema JWT, pa ni ID
		if (!(authentication instanceof JwtAuthenticationToken jwt)) {
			return false;
		}
		UUID korisnikId = UUID.fromString(jwt.getToken().getSubject());

		return members.findById(new OrganizationMember.Id(organizationId, korisnikId))
				.map(clanstvo -> List.of(uloge).contains(clanstvo.getRole()))
				.orElse(false);
	}

}
