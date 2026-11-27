package ba.slotbook.chat;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.chat.ChatModel.Poruka;
import ba.slotbook.chat.ChatRepository.SacuvanaPoruka;

@Service
public class ChatSessionService {

	private final ChatRepository chat;
	private final Clock clock;
	private final int maxPoruka;

	// Sesija ima granicu umjesto rezanja starih poruka. Rezanje bi moglo odvojiti rezultat alata od poziva
	// koji ga je tražio, a provajder takvu historiju odbije. Puna sesija znači: otvori novu.
	ChatSessionService(ChatRepository chat, Clock clock, @Value("${slotbook.ai.max-messages}") int maxPoruka) {
		this.chat = chat;
		this.clock = clock;
		this.maxPoruka = maxPoruka;
	}

	@Transactional
	public ChatSessionResponse open(UUID organizationId, UUID userId) {
		UUID id = UUID.randomUUID();
		Instant sada = clock.instant();
		chat.novaSesija(id, organizationId, userId, sada);
		return new ChatSessionResponse(id, sada);
	}

	// Na ekranu je samo ono što su napisali korisnik i asistent. Pozivi alata i rezultati su za model.
	@Transactional(readOnly = true)
	public List<ChatMessageResponse> history(UUID sessionId) {
		return chat.poruke(sessionId).stream()
				.filter(ChatSessionService::zaEkran)
				.map(s -> new ChatMessageResponse(s.poruka().role(), s.poruka().content(), s.vrijeme()))
				.toList();
	}

	@Transactional(readOnly = true)
	List<Poruka> zaModel(UUID sessionId) {
		return chat.poruke(sessionId).stream().map(SacuvanaPoruka::poruka).toList();
	}

	// Sve ili ništa. Lock na redu sesije drži dvije istovremene poruke u istoj sesiji u redu.
	@Transactional
	void dopisi(UUID sessionId, List<Poruka> nove) {
		int broj = chat.zakljucajIBroj(sessionId);
		if (broj + nove.size() > maxPoruka) {
			throw new ChatSessionFullException();
		}
		Instant sada = clock.instant();
		for (Poruka poruka : nove) {
			broj++;
			chat.dopisi(sessionId, broj, poruka, sada);
		}
		chat.zapisiBroj(sessionId, broj, sada);
	}

	private static boolean zaEkran(SacuvanaPoruka sacuvana) {
		Poruka poruka = sacuvana.poruka();
		return !"tool".equals(poruka.role()) && poruka.content() != null && !poruka.content().isBlank();
	}

}
