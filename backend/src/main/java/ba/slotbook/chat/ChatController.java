package ba.slotbook.chat;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

	private final ChatSessionService sessions;

	public ChatController(ChatSessionService sessions) {
		this.sessions = sessions;
	}

	// Svaki član može pitati asistenta, a alati poslije vide samo ono što njegova uloga smije
	@PostMapping("/api/organizations/{id}/chat/sessions")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@organizationAccess.hasRole(#id, authentication, 'ADMIN', 'PROVIDER', 'CLIENT')")
	public ChatSessionResponse open(@PathVariable UUID id, @AuthenticationPrincipal Jwt token) {
		return sessions.open(id, UUID.fromString(token.getSubject()));
	}

	// Najviše slotbook.ai.max-messages poruka, pa bez stranica
	@GetMapping("/api/chat/sessions/{sessionId}/messages")
	@PreAuthorize("@chatSessionAccess.isOwner(#sessionId, authentication)")
	public List<ChatMessageResponse> history(@PathVariable UUID sessionId) {
		return sessions.history(sessionId);
	}

}
