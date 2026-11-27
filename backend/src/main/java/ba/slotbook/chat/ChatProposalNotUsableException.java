package ba.slotbook.chat;

public class ChatProposalNotUsableException extends RuntimeException {

	public enum Razlog {
		ISTEKAO, ODBIJEN, POTVRDJEN, RESURS_NEDOSTUPAN, PLACA_SE
	}

	private final Razlog razlog;

	public ChatProposalNotUsableException(Razlog razlog) {
		super("Prijedlog se ne može iskoristiti: " + razlog);
		this.razlog = razlog;
	}

	public Razlog razlog() {
		return razlog;
	}

}
