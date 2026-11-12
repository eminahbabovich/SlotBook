package ba.slotbook.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.error.ProbniKontroler;

@Import({ TestcontainersConfiguration.class, ProbniKontroler.class })
@SpringBootTest
@AutoConfigureMockMvc
class SecurityApiTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void zasticenaAdresaBezPrijaveVraca401KaoProblemDetail() throws Exception {
		mockMvc.perform(get("/probno/greska"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.title").value("Prijava je potrebna"));
	}

	@Test
	void bezPrijaveSeNeMozeSaznatiKojeAdresePostoje() throws Exception {
		mockMvc.perform(get("/ne-postoji"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void healthJeOtvorenBezPrijaveJerGaRenderProvjerava() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk());
	}

	@Test
	@WithMockUser
	void zabranaIzKontroleraVraca403UmjestoServerskeGreske() throws Exception {
		mockMvc.perform(get("/probno/zabranjeno"))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.title").value("Pristup nije dozvoljen"));
	}

}
