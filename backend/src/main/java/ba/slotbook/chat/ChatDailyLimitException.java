package ba.slotbook.chat;

// zaCijeluAplikaciju: potrošena je zajednička kvota, a ne dio ovog korisnika. Poruka korisniku se razlikuje.
public class ChatDailyLimitException extends RuntimeException {

	private final long sekundiDoPonoci;
	private final boolean zaCijeluAplikaciju;

	public ChatDailyLimitException(long sekundiDoPonoci, boolean zaCijeluAplikaciju) {
		super(zaCijeluAplikaciju ? "Potrošena dnevna granica aplikacije" : "Potrošena dnevna granica korisnika");
		this.sekundiDoPonoci = sekundiDoPonoci;
		this.zaCijeluAplikaciju = zaCijeluAplikaciju;
	}

	public long sekundiDoPonoci() {
		return sekundiDoPonoci;
	}

	public boolean zaCijeluAplikaciju() {
		return zaCijeluAplikaciju;
	}

}
