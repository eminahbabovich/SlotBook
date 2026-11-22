package ba.slotbook.observability;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class MetricsApiTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void metrikeBezPrijaveNisuVidljive() throws Exception {
		mockMvc.perform(get("/actuator/metrics/slotbook.bookings.created"))
				.andExpect(status().isUnauthorized());
	}

	// Brojači postoje od starta, i prije prve rezervacije, pa graf ne počinje rupom
	@Test
	@WithMockUser
	void lokalnoSeVideTriBrojacaSlotBooka() throws Exception {
		for (String ime : new String[] { "slotbook.bookings.created", "slotbook.bookings.conflicts",
				"slotbook.mail.failed" }) {
			mockMvc.perform(get("/actuator/metrics/" + ime))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.name").value(ime));
		}
	}

}
