package ba.slotbook.organization;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
class MyOrganizationsApiTest {

	private static final String LOZINKA = "trebević1984";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository users;

	@Autowired
	OrganizationRepository organizations;

	@Autowired
	OrganizationMemberRepository members;

	private String lejla;
	private String edin;

	// Lejla vodi čitaonicu i klijentica je Edinove teretane. Edin ima još i konsultacije.
	// Baza se dijeli između testova bez rollbacka, pa se sve pravi samo ako već ne postoji.
	@BeforeEach
	void lejlaAdminCitaoniceIKlijentTeretaneEdinImaDvijeOrganizacije() throws Exception {
		lejla = token("lejla.moje@example.com", "Lejla");
		edin = token("edin.moje@example.com", "Edin");
		organizacija(lejla, "citaonica-pmf-moje", "Čitaonica PMF");
		UUID teretana = organizacija(edin, "teretana-moje", "Teretana Ilidža");
		organizacija(edin, "konsultacije-moje", "Konsultacije ETF");

		var lejlaId = users.findByEmail("lejla.moje@example.com").orElseThrow();
		var kljuc = new OrganizationMember.Id(teretana, lejlaId.getId());
		if (!members.existsById(kljuc)) {
			members.save(new OrganizationMember(organizations.findById(teretana).orElseThrow(), lejlaId,
					OrganizationRole.CLIENT, Instant.parse("2026-11-15T10:00:00Z")));
		}
	}

	private String token(String email, String ime) throws Exception {
		if (users.findByEmail(email).isEmpty()) {
			mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
					{"email": "%s", "password": "%s", "displayName": "%s"}
					""".formatted(email, LOZINKA, ime))).andExpect(status().isCreated());
		}
		String tijelo = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "%s"}
				""".formatted(email, LOZINKA))).andReturn().getResponse().getContentAsString();
		return JsonPath.read(tijelo, "$.accessToken");
	}

	private UUID organizacija(String token, String slug, String naziv) throws Exception {
		var postojeca = organizations.findBySlug(slug);
		if (postojeca.isPresent()) {
			return postojeca.get().getId();
		}
		String tijelo = mockMvc.perform(post("/api/organizations")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "%s", "slug": "%s", "timezone": "Europe/Sarajevo"}
						""".formatted(naziv, slug)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(tijelo, "$.id"));
	}

	private ResultActions moje(String token) throws Exception {
		return mockMvc.perform(get("/api/me/organizations").header("Authorization", "Bearer " + token));
	}

	@Test
	void korisnikVidiSvojeOrganizacijePoNazivuSaSvojomUlogomIZonom() throws Exception {
		moje(lejla)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[*].name", contains("Čitaonica PMF", "Teretana Ilidža")))
				.andExpect(jsonPath("$[*].role", contains("ADMIN", "CLIENT")))
				.andExpect(jsonPath("$[0].slug").value("citaonica-pmf-moje"))
				.andExpect(jsonPath("$[0].timezone").value("Europe/Sarajevo"))
				.andExpect(jsonPath("$[0].id").isString());
	}

	@Test
	void tudjaOrganizacijaNijeNaListi() throws Exception {
		moje(edin)
				.andExpect(jsonPath("$[*].slug", contains("konsultacije-moje", "teretana-moje")))
				.andExpect(jsonPath("$[*].slug", not(hasItem("citaonica-pmf-moje"))));
	}

	@Test
	void korisnikBezOrganizacijaDobijePraznuListu() throws Exception {
		moje(token("novi.moje@example.com", "Novi")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void bezTokenaVraca401() throws Exception {
		mockMvc.perform(get("/api/me/organizations")).andExpect(status().isUnauthorized());
	}

}
