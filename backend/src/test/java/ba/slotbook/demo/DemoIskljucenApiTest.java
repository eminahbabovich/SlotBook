package ba.slotbook.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import ba.slotbook.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

// Bez profila demo nema ni računa ni brisanja baze. Ista konfiguracija kao ostali ApiTest-ovi, pa se
// kontekst dijeli s njima i baza ima njihove podatke.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class DemoIskljucenApiTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void bezProfilaDemoNemaDemoRacuna() throws Exception {
		mockMvc.perform(get("/api/demo")).andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("select count(*) from users where email like '%@slotbook.test'",
				Integer.class)).isZero();
	}

}
