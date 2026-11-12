package ba.slotbook.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.user.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OrganizationApiTest {

	private static final String EMAIL = "sabina.org@example.com";
	private static final String LOZINKA = "baščaršija1462";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository users;

	@Autowired
	OrganizationMemberRepository members;

	private String token;

	// Kontekst i baza se dijele između testova bez rollbacka, pa korisnik nastaje samo jednom
	@BeforeEach
	void prijavljenKorisnik() throws Exception {
		if (users.findByEmail(EMAIL).isEmpty()) {
			mockMvc.perform(post("/api/auth/register")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"email": "%s", "password": "%s", "displayName": "Sabina"}
							""".formatted(EMAIL, LOZINKA)))
					.andExpect(status().isCreated());
		}
		String tijelo = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "%s", "password": "%s"}
						""".formatted(EMAIL, LOZINKA)))
				.andReturn().getResponse().getContentAsString();
		token = JsonPath.read(tijelo, "$.accessToken");
	}

	private ResultActions napravi(String slug, String zona) throws Exception {
		return mockMvc.perform(post("/api/organizations")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Čitaonica FIT", "slug": "%s", "timezone": "%s"}
						""".formatted(slug, zona)));
	}

	@Test
	void kreatorPostajeAdminNoveOrganizacije() throws Exception {
		String tijelo = napravi("citaonica-fit", "Europe/Sarajevo")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Čitaonica FIT"))
				.andExpect(jsonPath("$.timezone").value("Europe/Sarajevo"))
				.andReturn().getResponse().getContentAsString();

		UUID organizacijaId = UUID.fromString(JsonPath.read(tijelo, "$.id"));
		UUID korisnikId = users.findByEmail(EMAIL).orElseThrow().getId();
		assertThat(members.findById(new OrganizationMember.Id(organizacijaId, korisnikId)))
				.get().extracting(OrganizationMember::getRole).isEqualTo(OrganizationRole.ADMIN);
	}

	@Test
	void zauzetaAdresaVraca409() throws Exception {
		napravi("sala-etf", "Europe/Sarajevo").andExpect(status().isCreated());

		napravi("sala-etf", "Europe/Sarajevo")
				.andExpect(status().isConflict())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Adresa je zauzeta"));
	}

	@Test
	void zonaKaoFiksniPomakSeOdbija() throws Exception {
		napravi("teretana-grbavica", "+01:00")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.greske[0].polje").value("timezone"));
	}

	@Test
	void bezTokenaSeOrganizacijaNeMozeNapraviti() throws Exception {
		mockMvc.perform(post("/api/organizations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Konsultacije", "slug": "konsultacije-pmf", "timezone": "Europe/Sarajevo"}
						"""))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void javniProfilSeVidiBezPrijave() throws Exception {
		napravi("bazen-otoka", "Europe/Sarajevo").andExpect(status().isCreated());

		mockMvc.perform(get("/api/organizations/bazen-otoka"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.slug").value("bazen-otoka"));
	}

	@Test
	void nepostojecaOrganizacijaVraca404() throws Exception {
		mockMvc.perform(get("/api/organizations/ne-postoji"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Organizacija ne postoji"));
	}

}
