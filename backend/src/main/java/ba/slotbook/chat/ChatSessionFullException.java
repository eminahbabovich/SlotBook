package ba.slotbook.chat;

public class ChatSessionFullException extends RuntimeException {

	public ChatSessionFullException() {
		super("Sesija je dostigla najveći broj poruka");
	}

}
