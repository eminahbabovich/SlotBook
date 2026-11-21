package ba.slotbook.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.error.ProbniKontroler;

// Format loga (tekst ili JSON) se ovdje ne provjerava: Logback se podesi jednom po JVM-u, pa u cijeloj
// suiti format zavisi od toga koji je kontekst krenuo prvi. Da prod uključuje JSON provjerava ProdProfilTest.
@Import({ TestcontainersConfiguration.class, ProbniKontroler.class })
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class RequestIdApiTest {

	@Autowired
	MockMvc mockMvc;

	// Filter je prije Spring Securityja, pa ID ima i odbijen zahtjev
	@Test
	void odbijenZahtjevBezPrijaveImaIdZahtjeva() throws Exception {
		mockMvc.perform(get("/api/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("X-Request-Id", matchesPattern("[0-9a-f-]{36}")));
	}

	@Test
	@WithMockUser
	void neocekivanaGreskaJeULoguSIdomIzOdgovora(CapturedOutput izlaz) throws Exception {
		String id = mockMvc.perform(get("/probno/greska"))
				.andExpect(status().isInternalServerError())
				.andReturn().getResponse().getHeader("X-Request-Id");

		assertThat(izlaz.getOut().lines().filter(l -> l.contains("Neočekivana greška"))).singleElement()
				.asString().contains(id);
	}

	// Korisnik koji javi grešku pošalje ID iz poruke, a po njemu se u logu nađe stack trace
	@Test
	@WithMockUser
	void neocekivanaGreskaVracaIdZahtjevaUTijelu() throws Exception {
		MvcResult rezultat = mockMvc.perform(get("/probno/greska"))
				.andExpect(status().isInternalServerError())
				.andReturn();

		assertThat(JsonPath.<String>read(rezultat.getResponse().getContentAsString(), "$.requestId"))
				.isEqualTo(rezultat.getResponse().getHeader("X-Request-Id"));
	}

}
