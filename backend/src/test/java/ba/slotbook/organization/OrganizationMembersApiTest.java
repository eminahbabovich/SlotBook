package ba.slotbook.organization;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.user.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OrganizationMembersApiTest {

	private static final String LOZINKA = "trebević1984";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	UserRepository users;

	@Autowired
	OrganizationRepository organizations;

	@Autowired
	OrganizationMemberRepository members;

	private String amar;
	private String selma;
	private String tarik;
	private UUID salaEtf;

	// Baza se dijeli između testova bez rollbacka, pa se sve pravi samo ako već ne postoji
	@BeforeEach
	void amarJeAdminSelmaKlijentTarikAdminDrugeOrganizacije() throws Exception {
		amar = token("amar.clanovi@example.com", "Amar");
		selma = token("selma.clanovi@example.com", "Selma");
		tarik = token("tarik.clanovi@example.com", "Tarik");

		salaEtf = organizacija(amar, "sala-etf-clanovi");
		organizacija(tarik, "teretana-tarik");

		var selmaId = users.findByEmail("selma.clanovi@example.com").orElseThrow();
		var kljuc = new OrganizationMember.Id(salaEtf, selmaId.getId());
		if (!members.existsById(kljuc)) {
			members.save(new OrganizationMember(organizations.findById(salaEtf).orElseThrow(), selmaId,
					OrganizationRole.CLIENT, Instant.parse("2026-11-12T10:00:00Z")));
		}
	}

	private String token(String email, String ime) throws Exception {
		if (users.findByEmail(email).isEmpty()) {
			mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
					{"email": "%s", "password": "%s", "displayName": "%s"}
					""".formatted(email, LOZINKA, ime))).andExpect(status().isAccepted());
			jdbc.update("update users set email_verified_at = now() where email = lower(?)", email);
		}
		String tijelo = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "%s"}
				""".formatted(email, LOZINKA))).andReturn().getResponse().getContentAsString();
		return JsonPath.read(tijelo, "$.accessToken");
	}

	private UUID organizacija(String token, String slug) throws Exception {
		var postojeca = organizations.findBySlug(slug);
		if (postojeca.isPresent()) {
			return postojeca.get().getId();
		}
		String tijelo = mockMvc.perform(post("/api/organizations")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Organizacija %s", "slug": "%s", "timezone": "Europe/Sarajevo"}
						""".formatted(slug, slug)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(tijelo, "$.id"));
	}

	private ResultActions clanovi(UUID organizacijaId, String token) throws Exception {
		return mockMvc.perform(get("/api/organizations/{id}/members", organizacijaId)
				.header("Authorization", "Bearer " + token));
	}

	@Test
	void adminVidiClanoveSUlogamaBezHashaLozinke() throws Exception {
		clanovi(salaEtf, amar)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[*].displayName", containsInAnyOrder("Amar", "Selma")))
				.andExpect(jsonPath("$.content[*].role", containsInAnyOrder("ADMIN", "CLIENT")))
				.andExpect(content().string(not(containsString("password"))));
	}

	// Selma ima fiksni joinedAt (12.11.), a Amar je ušao kad je napravio organizaciju, sa stvarnim satom
	@Test
	void clanoviIduPoDatumuUlaskaJedanPoStranici() throws Exception {
		mockMvc.perform(get("/api/organizations/{id}/members?size=1", salaEtf)
				.header("Authorization", "Bearer " + amar))
				.andExpect(jsonPath("$.content[*].displayName", contains("Selma")))
				.andExpect(jsonPath("$.totalPages").value(2));
	}

	@Test
	void klijentIsteOrganizacijeDobija403() throws Exception {
		clanovi(salaEtf, selma)
				.andExpect(status().isForbidden())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void adminTudjeOrganizacijeDobija403() throws Exception {
		clanovi(salaEtf, tarik).andExpect(status().isForbidden());
	}

	@Test
	void nepostojecaOrganizacijaIzgledaIstoKaoTudja() throws Exception {
		clanovi(UUID.randomUUID(), amar).andExpect(status().isForbidden());
	}

	@Test
	void bezTokenaVraca401() throws Exception {
		mockMvc.perform(get("/api/organizations/{id}/members", salaEtf))
				.andExpect(status().isUnauthorized());
	}

}
