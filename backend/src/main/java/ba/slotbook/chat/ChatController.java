package ba.slotbook.chat;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
public class ChatController {

	private final ChatSessionService sessions;
	private final ChatProposalService proposals;
	private final ChatService chat;

	public ChatController(ChatSessionService sessions, ChatProposalService proposals, ChatService chat) {
		this.sessions = sessions;
		this.proposals = proposals;
		this.chat = chat;
	}

	// Jedna poruka i odgovor asistenta. Odgovor čeka model i alate, pa traje i nekoliko sekundi.
	@PostMapping("/api/chat/sessions/{sessionId}/messages")
	@PreAuthorize("@chatSessionAccess.isOwner(#sessionId, authentication)")
	public ChatReplyResponse send(@PathVariable UUID sessionId, @Valid @RequestBody SendChatMessageRequest request,
			@AuthenticationPrincipal Jwt token, Authentication authentication) {
		return chat.send(sessionId, UUID.fromString(token.getSubject()), request.text(), authentication);
	}

	// Jedini put kojim chat mijenja rezervacije: dugme u widgetu, a ne tekst koji je napisao model
	@PostMapping("/api/chat/proposals/{proposalId}/confirm")
	@PreAuthorize("@chatSessionAccess.isProposalOwner(#proposalId, authentication)")
	public ChatProposalResponse confirm(@PathVariable UUID proposalId, Authentication authentication) {
		return proposals.confirm(proposalId, authentication);
	}

	@PostMapping("/api/chat/proposals/{proposalId}/dismiss")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("@chatSessionAccess.isProposalOwner(#proposalId, authentication)")
	public void dismiss(@PathVariable UUID proposalId) {
		proposals.dismiss(proposalId);
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
