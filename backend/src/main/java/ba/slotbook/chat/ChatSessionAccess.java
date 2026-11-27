package ba.slotbook.chat;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

// Poziva se iz @PreAuthorize kao @chatSessionAccess.isOwner(...). Razgovor vidi samo onaj ko ga je
// otvorio, ni admin organizacije. Nepostojeća sesija je isto false (403), da se ne vidi šta postoji.
// Članstvo se ne provjerava posebno: kad članstvo nestane, baza obriše i sesiju.
@Component
public class ChatSessionAccess {

	private final ChatRepository chat;

	ChatSessionAccess(ChatRepository chat) {
		this.chat = chat;
	}

	public boolean isOwner(UUID sessionId, Authentication authentication) {
		if (!(authentication instanceof JwtAuthenticationToken jwt)) {
			return false;
		}
		UUID korisnik = UUID.fromString(jwt.getToken().getSubject());
		return chat.vlasnik(sessionId).map(korisnik::equals).orElse(false);
	}

}
