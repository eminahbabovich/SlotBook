package ba.slotbook.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import ba.slotbook.TestcontainersConfiguration;

@Import({ TestcontainersConfiguration.class, ProbniKontroler.class })
@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerApiTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void neispravniPodaciVracajuPopisPoljaSaPorukama() throws Exception {
		mockMvc.perform(post("/probno/validacija")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"ime\":\"\",\"broj\":0}"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.title").value("Neispravni podaci"))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.greske[?(@.polje == 'ime')].poruka").value("Unesi ime."))
				.andExpect(jsonPath("$.greske[?(@.polje == 'broj')].poruka").value("Broj mora biti najmanje 1."));
	}

	@Test
	void nepostojecaAdresaVracaPrevedeniProblemDetail() throws Exception {
		mockMvc.perform(get("/ne-postoji"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.title").value("Adresa ne postoji"));
	}

	@Test
	void pokvarenJsonVracaBadRequest() throws Exception {
		mockMvc.perform(post("/probno/validacija")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{ovo nije json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Neispravan zahtjev"));
	}

	@Test
	void neocekivanaGreskaVracaServerErrorBezInternihDetalja() throws Exception {
		mockMvc.perform(get("/probno/greska"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.title").value("Greška na serveru"))
				.andExpect(jsonPath("$.detail").value(not(containsString("interna poruka"))));
	}

}
