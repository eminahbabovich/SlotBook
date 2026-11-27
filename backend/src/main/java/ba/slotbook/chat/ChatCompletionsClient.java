package ba.slotbook.chat;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

// Jedan klijent za svakog provajdera s OpenAI /chat/completions API-jem. Provajder se mijenja
// varijablama okruženja (AI_BASE_URL, AI_MODEL, AI_API_KEY), ne kodom.
@Component
class ChatCompletionsClient implements ChatModel {

	private static final long SEKUNDI_AKO_NE_KAZE = 60;

	private final RestClient http;
	private final String model;
	private final int maxTokena;
	private final boolean imaKljuc;

	// Bez ključa (CI, lokalno bez .env) aplikacija se pokrene, a asistent javi da nije dostupan.
	// Nema ponovnog pokušaja: svaki pokušaj troši istu besplatnu kvotu koju dijele svi korisnici.
	@Autowired
	ChatCompletionsClient(@Value("${slotbook.ai.base-url}") String adresa, @Value("${slotbook.ai.api-key}") String kljuc,
			@Value("${slotbook.ai.model}") String model, @Value("${slotbook.ai.max-tokens}") int maxTokena,
			@Value("${slotbook.ai.timeout}") Duration timeout) {
		this(RestClient.builder().requestFactory(saTimeoutom(timeout)), adresa, kljuc, model, maxTokena);
	}

	// Test ovdje daje builder vezan za lažni server
	ChatCompletionsClient(RestClient.Builder builder, String adresa, String kljuc, String model, int maxTokena) {
		this.http = builder.baseUrl(adresa).defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + kljuc).build();
		this.model = model;
		this.maxTokena = maxTokena;
		this.imaKljuc = !kljuc.isBlank();
	}

	@Override
	public Odgovor odgovori(List<Poruka> poruke, List<Alat> alati) {
		if (!imaKljuc) {
			throw new NedostupanException("AI_API_KEY nije postavljen", null);
		}
		OdgovorApi odgovor;
		try {
			odgovor = http.post().uri("/chat/completions").contentType(MediaType.APPLICATION_JSON)
					.body(new ZahtjevApi(model, poruke, alati.isEmpty() ? null : alati, maxTokena))
					.retrieve()
					.body(OdgovorApi.class);
		} catch (RestClientResponseException e) {
			if (e.getStatusCode().value() == 429) {
				throw new ZauzetException(sekundiDoPonovo(e.getResponseHeaders()), e);
			}
			throw new NedostupanException(
					"Provajder je vratio " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString(), e);
		} catch (ResourceAccessException e) {
			throw new NedostupanException("Provajder nije odgovorio: " + e.getMessage(), e);
		}
		if (odgovor == null || odgovor.choices() == null || odgovor.choices().isEmpty()) {
			throw new NedostupanException("Provajder je vratio odgovor bez choices", null);
		}
		var izbor = odgovor.choices().getFirst();
		var potrosnja = odgovor.usage() != null ? odgovor.usage() : new OdgovorApi.Potrosnja(0, 0);
		return new Odgovor(izbor.message(), izbor.finishReason(), potrosnja.promptTokens(),
				potrosnja.completionTokens());
	}

	private static long sekundiDoPonovo(HttpHeaders zaglavlja) {
		String vrijednost = zaglavlja == null ? null : zaglavlja.getFirst("retry-after");
		try {
			return vrijednost == null ? SEKUNDI_AKO_NE_KAZE : Math.max(1, (long) Math.ceil(Double.parseDouble(vrijednost)));
		} catch (NumberFormatException e) {
			return SEKUNDI_AKO_NE_KAZE;
		}
	}

	// Kratak timeout, jer korisnik gleda u chat dok čeka, a jedna poruka zna biti dva do četiri poziva
	private static SimpleClientHttpRequestFactory saTimeoutom(Duration timeout) {
		var fabrika = new SimpleClientHttpRequestFactory();
		fabrika.setConnectTimeout(Duration.ofSeconds(5));
		fabrika.setReadTimeout(timeout);
		return fabrika;
	}

	// max_tokens je kod Groqa zastario, ide max_completion_tokens
	@JsonInclude(JsonInclude.Include.NON_NULL)
	record ZahtjevApi(String model, List<Poruka> messages, List<Alat> tools,
			@JsonProperty("max_completion_tokens") int maxCompletionTokens) {
	}

	record OdgovorApi(List<Izbor> choices, Potrosnja usage) {

		record Izbor(Poruka message, @JsonProperty("finish_reason") String finishReason) {
		}

		record Potrosnja(@JsonProperty("prompt_tokens") int promptTokens,
				@JsonProperty("completion_tokens") int completionTokens) {
		}

	}

}
