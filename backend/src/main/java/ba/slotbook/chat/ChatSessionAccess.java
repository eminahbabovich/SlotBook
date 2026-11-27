package ba.slotbook.chat;

import java.util.Optional;
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
	private final ChatProposalRepository prijedlozi;

	ChatSessionAccess(ChatRepository chat, ChatProposalRepository prijedlozi) {
		this.chat = chat;
		this.prijedlozi = prijedlozi;
	}

	public boolean isOwner(UUID sessionId, Authentication authentication) {
		return korisnik(authentication).map(k -> chat.vlasnik(sessionId).map(k::equals).orElse(false)).orElse(false);
	}

	// Prijedlog potvrđuje samo vlasnik razgovora u kojem je nastao, ni admin organizacije
	public boolean isProposalOwner(UUID proposalId, Authentication authentication) {
		return korisnik(authentication).map(k -> prijedlozi.vlasnik(proposalId).map(k::equals).orElse(false))
				.orElse(false);
	}

	private static Optional<UUID> korisnik(Authentication authentication) {
		if (!(authentication instanceof JwtAuthenticationToken jwt)) {
			return Optional.empty();
		}
		return Optional.of(UUID.fromString(jwt.getToken().getSubject()));
	}

}
