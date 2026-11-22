package ba.slotbook.booking;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

// Iz liste se ispisuje samo onaj ko se upisao. Admin ne briše tuđe upise, termin ionako ide redom.
@Component
public class WaitlistAccess {

	private final WaitlistEntryRepository entries;

	public WaitlistAccess(WaitlistEntryRepository entries) {
		this.entries = entries;
	}

	public boolean isOwner(UUID entryId, Authentication authentication) {
		if (!(authentication instanceof JwtAuthenticationToken jwt)) {
			return false;
		}
		UUID korisnikId = UUID.fromString(jwt.getToken().getSubject());
		return entries.findById(entryId).map(upis -> upis.getClient().getId().equals(korisnikId)).orElse(false);
	}

}
