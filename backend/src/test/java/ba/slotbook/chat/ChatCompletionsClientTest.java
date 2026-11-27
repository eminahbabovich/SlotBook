package ba.slotbook.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import ba.slotbook.chat.ChatModel.Alat;
import ba.slotbook.chat.ChatModel.Poruka;
import ba.slotbook.chat.ChatModel.PozivAlata;

// Šta tačno ide provajderu i kako se čita odgovor, na lažnom serveru, bez mreže i bez trošenja kvote.
// Pravi poziv na Groq se provjerava ručno s ključem.
class ChatCompletionsClientTest {

	private static final String ADRESA = "https://api.groq.com/openai/v1";

	private final RestClient.Builder builder = RestClient.builder();
	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
	private final ChatCompletionsClient klijent = new ChatCompletionsClient(builder, ADRESA, "gsk_lazni",
			"openai/gpt-oss-120b", 1024);

	private static final Alat RESURSI = Alat.funkcija("list_resources", "Resursi organizacije",
			Map.of("type", "object", "properties", Map.of()));

	@Test
	void saljeModelPorukeIAlateUFormatuChatCompletionsBezPraznihPolja() {
		// STRICT: polje koje nije ovdje (npr. "tool_calls": null) obara test
		server.expect(requestTo(ADRESA + "/chat/completions"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "Bearer gsk_lazni"))
				.andExpect(content().json("""
						{"model": "openai/gpt-oss-120b", "max_completion_tokens": 1024,
						 "messages": [
						   {"role": "system", "content": "Pomažeš u rezervaciji termina."},
						   {"role": "user", "content": "Ima li slobodno mjesto u čitaonici sutra?"}],
						 "tools": [{"type": "function", "function": {"name": "list_resources",
						   "description": "Resursi organizacije", "parameters": {"type": "object", "properties": {}}}}]}
						""", JsonCompareMode.STRICT))
				.andRespond(withSuccess("""
						{"choices": [{"index": 0, "message": {"role": "assistant", "content": "Ima, od 10:00."},
						  "finish_reason": "stop"}], "usage": {"prompt_tokens": 210, "completion_tokens": 12}}
						""", MediaType.APPLICATION_JSON));

		var odgovor = klijent.odgovori(List.of(Poruka.sistem("Pomažeš u rezervaciji termina."),
				Poruka.korisnik("Ima li slobodno mjesto u čitaonici sutra?")), List.of(RESURSI));

		server.verify();
		assertThat(odgovor.poruka().content()).isEqualTo("Ima, od 10:00.");
		assertThat(odgovor.razlog()).isEqualTo("stop");
	}

	@Test
	void citaPozivAlataIPotrosnjuIPreskaceNepoznataPolja() {
		// reasoning i x_groq Groq stvarno vraća, a mi ih ne koristimo
		server.expect(requestTo(ADRESA + "/chat/completions")).andRespond(withSuccess("""
				{"id": "chatcmpl-1", "x_groq": {"id": "req_1"},
				 "choices": [{"index": 0, "finish_reason": "tool_calls", "message": {"role": "assistant",
				   "reasoning": "Treba mi lista resursa.",
				   "tool_calls": [{"id": "call_7", "type": "function",
				     "function": {"name": "list_resources", "arguments": "{}"}}]}}],
				 "usage": {"prompt_tokens": 350, "completion_tokens": 40, "total_tokens": 390, "queue_time": 0.01}}
				""", MediaType.APPLICATION_JSON));

		var odgovor = klijent.odgovori(List.of(Poruka.korisnik("Šta mogu rezervisati?")), List.of(RESURSI));

		assertThat(odgovor.razlog()).isEqualTo("tool_calls");
		assertThat(odgovor.poruka().toolCalls()).singleElement().satisfies(poziv -> {
			assertThat(poziv.id()).isEqualTo("call_7");
			assertThat(poziv.function().name()).isEqualTo("list_resources");
			assertThat(poziv.function().arguments()).isEqualTo("{}");
		});
		assertThat(odgovor.ulazniTokeni()).isEqualTo(350);
		assertThat(odgovor.izlazniTokeni()).isEqualTo(40);
	}

	@Test
	void rezultatAlataIdeNazadUzIdPozivaIPorukuKojaGaJeTrazila() {
		var trazio = new Poruka("assistant", null, List.of(new PozivAlata("call_7", "function",
				new PozivAlata.Funkcija("list_resources", "{}"))), null);
		server.expect(content().json("""
				{"messages": [
				   {"role": "user", "content": "Šta mogu rezervisati?"},
				   {"role": "assistant", "tool_calls": [{"id": "call_7", "type": "function",
				     "function": {"name": "list_resources", "arguments": "{}"}}]},
				   {"role": "tool", "tool_call_id": "call_7", "content": "[{\\"naziv\\": \\"Čitaonica, 2. sprat\\"}]"}]}
				""", JsonCompareMode.LENIENT))
				.andRespond(withSuccess("""
						{"choices": [{"message": {"role": "assistant", "content": "Čitaonicu."}, "finish_reason": "stop"}]}
						""", MediaType.APPLICATION_JSON));

		var odgovor = klijent.odgovori(List.of(Poruka.korisnik("Šta mogu rezervisati?"), trazio,
				Poruka.rezultatAlata("call_7", "[{\"naziv\": \"Čitaonica, 2. sprat\"}]")), List.of(RESURSI));

		server.verify();
		assertThat(odgovor.poruka().content()).isEqualTo("Čitaonicu.");
	}

	@Test
	void potrosenaKvotaDajeZauzetSaSekundamaIzRetryAfter() {
		server.expect(requestTo(ADRESA + "/chat/completions"))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("retry-after", "37"));

		assertThatThrownBy(() -> klijent.odgovori(List.of(Poruka.korisnik("Zdravo")), List.of()))
				.isInstanceOfSatisfying(ChatModel.ZauzetException.class,
						e -> assertThat(e.sekundi()).isEqualTo(37));
	}

	@Test
	void greskaProvajderaDajeNedostupan() {
		server.expect(requestTo(ADRESA + "/chat/completions")).andRespond(withServerError());

		assertThatThrownBy(() -> klijent.odgovori(List.of(Poruka.korisnik("Zdravo")), List.of()))
				.isInstanceOf(ChatModel.NedostupanException.class);
	}

	@Test
	void timeoutDajeNedostupan() {
		server.expect(requestTo(ADRESA + "/chat/completions"))
				.andRespond(withException(new SocketTimeoutException("Read timed out")));

		assertThatThrownBy(() -> klijent.odgovori(List.of(Poruka.korisnik("Zdravo")), List.of()))
				.isInstanceOf(ChatModel.NedostupanException.class);
	}

	@Test
	void bezKljucaNistaNeIdeProvajderu() {
		var bezKljuca = new ChatCompletionsClient(builder, ADRESA, "", "openai/gpt-oss-120b", 1024);

		// Da zahtjev ode, lažni server bi bacio AssertionError ("No further requests expected")
		assertThatThrownBy(() -> bezKljuca.odgovori(List.of(Poruka.korisnik("Zdravo")), List.of()))
				.isInstanceOf(ChatModel.NedostupanException.class);
		server.verify();
	}

}
