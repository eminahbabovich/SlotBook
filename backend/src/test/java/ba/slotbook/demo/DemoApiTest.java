package ba.slotbook.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import ba.slotbook.TestcontainersConfiguration;

// "default" uz "demo", da se učita i application-default iz testova (bez slanja mailova, BCrypt 4)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "default", "demo" })
class DemoApiTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	DemoPodaci podaci;

	// Svaki test kreće od svježeg dema: računi se "postare" za dva dana, pa provjera mora napuniti
	@BeforeEach
	void svjezDemo() {
		jdbc.update("update users set created_at = created_at - interval '2 days'");
		assertThat(podaci.osvjeziAkoTreba()).isTrue();
	}

	@Test
	void prijavaDobijaDemoRacuneIJavnuLozinku() throws Exception {
		mockMvc.perform(get("/api/demo"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lozinka").value(DemoPodaci.LOZINKA))
				.andExpect(jsonPath("$.racuni[*].email").value(contains("klijent@slotbook.test",
						"provider@slotbook.test", "admin@slotbook.test")));
		for (DemoRacun racun : DemoPodaci.RACUNI) {
			assertThat(token(racun.email())).isNotBlank();
		}
	}

	@Test
	void klijentImaKarteUSvimStanjima() throws Exception {
		mockMvc.perform(get("/api/bookings/mine").param("size", "50")
				.header("Authorization", "Bearer " + token("klijent@slotbook.test")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].status").value(hasItems("CONFIRMED", "PENDING", "COMPLETED",
						"CANCELLED")))
				.andExpect(jsonPath("$.content[*].organizationName").value(hasItems("Čitaonica PMF",
						"Teretana Ilidža")));
	}

	@Test
	void adminVidiStatistikuZaProsleCetiriSedmice() throws Exception {
		UUID citaonica = jdbc.queryForObject("select id from organizations where slug = 'citaonica-pmf'", UUID.class);
		LocalDate danas = LocalDate.now(ZoneId.of("Europe/Sarajevo"));
		mockMvc.perform(get("/api/organizations/{id}/stats", citaonica)
				.param("from", danas.minusDays(28).toString())
				.param("to", danas.toString())
				.header("Authorization", "Bearer " + token("admin@slotbook.test")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.byStatus.COMPLETED").value(greaterThan(50)))
				.andExpect(jsonPath("$.byStatus.CANCELLED").value(greaterThan(0)))
				.andExpect(jsonPath("$.byStatus.NO_SHOW").value(greaterThan(0)));
	}

	@Test
	void svjezDemoOstajeSPodacimaPosjetilaca() throws Exception {
		organizacijaPosjetioca();

		assertThat(podaci.osvjeziAkoTreba()).isFalse();
		assertThat(brojOrganizacija()).isEqualTo(3);
	}

	@Test
	void demoStarijiOdDanaSeVracaNaPocetak() throws Exception {
		organizacijaPosjetioca();
		jdbc.update("update users set created_at = created_at - interval '25 hours'");

		assertThat(podaci.osvjeziAkoTreba()).isTrue();
		assertThat(brojOrganizacija()).isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(9);
		assertThat(token("klijent@slotbook.test")).isNotBlank();
	}

	@Test
	void obrisanDemoRacunVracaDemoOdmah() throws Exception {
		mockMvc.perform(delete("/api/me")
				.header("Authorization", "Bearer " + token("klijent@slotbook.test"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"password": "%s"}
						""".formatted(DemoPodaci.LOZINKA)))
				.andExpect(status().isNoContent());

		assertThat(podaci.osvjeziAkoTreba()).isTrue();
		assertThat(token("klijent@slotbook.test")).isNotBlank();
	}

	private void organizacijaPosjetioca() throws Exception {
		mockMvc.perform(post("/api/organizations")
				.header("Authorization", "Bearer " + token("admin@slotbook.test"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Sala za sastanke", "slug": "sala-posjetioca", "timezone": "Europe/Sarajevo"}
						"""))
				.andExpect(status().isCreated());
	}

	private int brojOrganizacija() {
		return jdbc.queryForObject("select count(*) from organizations", Integer.class);
	}

	private String token(String email) throws Exception {
		String tijelo = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "%s"}
				""".formatted(email, DemoPodaci.LOZINKA)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(tijelo, "$.accessToken");
	}

}
