package ba.slotbook.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

// Model u testovima: vraća odgovore koje mu test zada, redom, i pamti šta je dobio. Ne pogađa ništa
// sam, pa test tačno zna koji alat je "model" pozvao. Prazan red znači da ga niko nije trebao zvati, pa
// baci grešku. Dijele ga svi testovi u kontekstu, zato ga test očisti prije sebe.
public class LazniChatModel implements ChatModel {

	private final ConcurrentLinkedQueue<Object> odgovori = new ConcurrentLinkedQueue<>();
	private final List<List<Poruka>> primljeno = new CopyOnWriteArrayList<>();
	private final AtomicInteger brojPoziva = new AtomicInteger();

	@Override
	public Odgovor odgovori(List<Poruka> poruke, List<Alat> alati) {
		primljeno.add(List.copyOf(poruke));
		Object sljedeci = odgovori.poll();
		if (sljedeci instanceof RuntimeException greska) {
			throw greska;
		}
		if (sljedeci == null) {
			throw new IllegalStateException("Lažni model nije imao zadan odgovor");
		}
		return (Odgovor) sljedeci;
	}

	public void ocisti() {
		odgovori.clear();
		primljeno.clear();
	}

	public LazniChatModel kaze(String tekst) {
		odgovori.add(new Odgovor(new Poruka("assistant", tekst, null, null), "stop", 100, 20));
		return this;
	}

	LazniChatModel zove(String alat, String argumenti) {
		odgovori.add(new Odgovor(poziv(alat, argumenti), "tool_calls", 100, 20));
		return this;
	}

	// Provajder je stao na max_completion_tokens usred poziva alata
	LazniChatModel odsijece(String alat, String argumenti) {
		odgovori.add(new Odgovor(poziv(alat, argumenti), "length", 100, 1024));
		return this;
	}

	LazniChatModel baci(RuntimeException greska) {
		odgovori.add(greska);
		return this;
	}

	List<List<Poruka>> primljeno() {
		return new ArrayList<>(primljeno);
	}

	private Poruka poziv(String alat, String argumenti) {
		String id = "poziv-" + brojPoziva.incrementAndGet();
		return new Poruka("assistant", null, List.of(new PozivAlata(id, "function",
				new PozivAlata.Funkcija(alat, argumenti))), null);
	}

}
