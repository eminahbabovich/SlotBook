package ba.slotbook.chat;

import java.util.List;

// Odgovor na jednu poruku: tekst asistenta i prijedlozi koje je napravio u tom krugu
public record ChatReplyResponse(String text, List<ChatProposalView> proposals) {
}
