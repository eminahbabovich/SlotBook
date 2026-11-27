package ba.slotbook.chat;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

// Interface ima dvije implementacije: pravi klijent i lažni u testovima, da CI ne zavisi od tuđeg servera
// ni od besplatne kvote. Kao StripeCheckout.
public interface ChatModel {

	Odgovor odgovori(List<Poruka> poruke, List<Alat> alati);

	// Polja su tačno kao u /chat/completions (Groq, OpenAI, Gemini), jer se poruka u tom obliku čuva u
	// historiji i šalje nazad. Prazna polja se ne šalju. Polja koja ne koristimo (reasoning) Jackson preskače sam.
	@JsonInclude(JsonInclude.Include.NON_NULL)
	record Poruka(String role, String content, @JsonProperty("tool_calls") List<PozivAlata> toolCalls,
			@JsonProperty("tool_call_id") String toolCallId) {

		public static Poruka sistem(String tekst) {
			return new Poruka("system", tekst, null, null);
		}

		public static Poruka korisnik(String tekst) {
			return new Poruka("user", tekst, null, null);
		}

		// idPoziva povezuje rezultat s pozivom. Bez njega provajder vrati 400.
		public static Poruka rezultatAlata(String idPoziva, String rezultat) {
			return new Poruka("tool", rezultat, null, idPoziva);
		}

	}

	// arguments je JSON kao string, onako kako ga je model napisao. Parsira ga alat, jer model zna
	// napisati i neispravan JSON.
	record PozivAlata(String id, String type, Funkcija function) {

		public record Funkcija(String name, String arguments) {
		}

	}

	// parameters je JSON Schema argumenata alata
	record Alat(String type, Definicija function) {

		public static Alat funkcija(String ime, String opis, Map<String, Object> parametri) {
			return new Alat("function", new Definicija(ime, opis, parametri));
		}

		public record Definicija(String name, String description, Map<String, Object> parameters) {
		}

	}

	// razlog je finish_reason: "stop", "tool_calls" ili "length" (odgovor odsječen na max tokena)
	record Odgovor(Poruka poruka, String razlog, int ulazniTokeni, int izlazniTokeni) {
	}

	// Nema ključa, timeout, ili je provajder vratio grešku. Detalji idu u log, ne korisniku.
	class NedostupanException extends RuntimeException {

		public NedostupanException(String poruka, Throwable uzrok) {
			super(poruka, uzrok);
		}

	}

	// Potrošena besplatna kvota kod provajdera (429). To nije naša dnevna granica po korisniku.
	class ZauzetException extends RuntimeException {

		private final long sekundi;

		public ZauzetException(long sekundi, Throwable uzrok) {
			super("Provajder traži da sačekamo " + sekundi + " s", uzrok);
			this.sekundi = sekundi;
		}

		public long sekundi() {
			return sekundi;
		}

	}

}
