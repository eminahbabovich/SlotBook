package ba.slotbook.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.error.ProbniKontroler;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;

// DSN je izmišljen. Događaj se uhvati u beforeSend i baci (vrati null), pa ništa ne ide na mrežu.
@Import({ TestcontainersConfiguration.class, ProbniKontroler.class, SentryApiTest.Uhvaceni.class })
@SpringBootTest(properties = "sentry.dsn=https://kljuc@sentry.example.com/1")
@AutoConfigureMockMvc
class SentryApiTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	List<SentryEvent> dogadjaji;

	@BeforeEach
	void prazno() {
		dogadjaji.clear();
	}

	@Test
	@WithMockUser
	void neocekivanaGreskaIdeUSentrySIdomZahtjeva() throws Exception {
		String requestId = mockMvc.perform(get("/probno/greska"))
				.andExpect(status().isInternalServerError())
				.andReturn().getResponse().getHeader("X-Request-Id");

		assertThat(dogadjaji).singleElement().satisfies(dogadjaj -> {
			assertThat(dogadjaj.getLevel()).isEqualTo(SentryLevel.ERROR);
			assertThat(dogadjaj.getThrowable()).isInstanceOf(IllegalStateException.class);
			assertThat(dogadjaj.getTag("requestId")).isEqualTo(requestId);
		});
	}

	// 4xx je odgovor na pogrešan zahtjev, ne greška u kodu. U Sentryju bi samo trošio besplatnu kvotu.
	@Test
	@WithMockUser
	void zabranaILosZahtjevNeIduUSentry() throws Exception {
		mockMvc.perform(get("/probno/zabranjeno")).andExpect(status().isForbidden());
		mockMvc.perform(post("/probno/validacija").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());

		assertThat(dogadjaji).isEmpty();
	}

	@TestConfiguration
	static class Uhvaceni {

		@Bean
		List<SentryEvent> dogadjaji() {
			return new CopyOnWriteArrayList<>();
		}

		@Bean
		SentryOptions.BeforeSendCallback uhvatiIBaci(List<SentryEvent> dogadjaji) {
			return (dogadjaj, hint) -> {
				dogadjaji.add(dogadjaj);
				return null;
			};
		}

	}

}
