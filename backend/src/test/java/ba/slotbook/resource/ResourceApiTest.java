package ba.slotbook.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
class ResourceApiTest {

	private static final String LOZINKA = "vrelo-bosne-1888";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	private String emina;
	private String kenan;
	private String dzenan;
	private UUID teretana;

	// Emina je admin teretane, Kenan trener (PROVIDER), Dženan član koji samo rezerviše (CLIENT).
	// Baza se dijeli između testova bez rollbacka, pa se sve pravi samo ako već ne postoji.
	@BeforeEach
	void teretanaSaTriUloge() throws Exception {
		emina = token("emina.resursi@example.com", "Emina");
		kenan = token("kenan.resursi@example.com", "Kenan");
		dzenan = token("dzenan.resursi@example.com", "Dženan");

		var postojeca = jdbc.queryForList("select id from organizations where slug = 'teretana-resursi'", UUID.class);
		teretana = postojeca.isEmpty() ? novaOrganizacija() : postojeca.getFirst();
		clan("kenan.resursi@example.com", "PROVIDER");
		clan("dzenan.resursi@example.com", "CLIENT");
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

	private UUID novaOrganizacija() throws Exception {
		String tijelo = mockMvc.perform(post("/api/organizations")
				.header("Authorization", "Bearer " + emina)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Teretana Ilidža", "slug": "teretana-resursi", "timezone": "Europe/Sarajevo"}
						"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(tijelo, "$.id"));
	}

	private void clan(String email, String uloga) {
		jdbc.update("insert into organization_members values (?, ?, ?, now()) on conflict do nothing",
				teretana, id(email), uloga);
	}

	private UUID id(String email) {
		return users.findByEmail(email).orElseThrow().getId();
	}

	private ResultActions napravi(String token, String tijelo) throws Exception {
		return mockMvc.perform(post("/api/organizations/{id}/resources", teretana)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(tijelo));
	}

	private long brojResursa() {
		return jdbc.queryForObject("select count(*) from resources where organization_id = ?", Long.class, teretana);
	}

	@Test
	void adminPraviResursIPostajeNjegovProvider() throws Exception {
		napravi(emina, """
				{"name": "Sala za funkcionalni trening", "description": "   ", "slotMinutes": 60}
				""")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.organizationId").value(teretana.toString()))
				.andExpect(jsonPath("$.providerId").value(id("emina.resursi@example.com").toString()))
				.andExpect(jsonPath("$.description").doesNotExist())
				.andExpect(jsonPath("$.slotMinutes").value(60))
				.andExpect(jsonPath("$.active").value(true));
	}

	@Test
	void trenerPraviResursKojiVodiOn() throws Exception {
		napravi(kenan, """
				{"name": "Individualni trening s Kenanom", "slotMinutes": 45}
				""")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.providerId").value(id("kenan.resursi@example.com").toString()));
	}

	@Test
	void klijentNeMozeNapravitiResurs() throws Exception {
		long prije = brojResursa();

		napravi(dzenan, """
				{"name": "Moja privatna sala", "slotMinutes": 60}
				""")
				.andExpect(status().isForbidden());

		assertThat(brojResursa()).isEqualTo(prije);
	}

	// Nova organizacija za svako pokretanje, da broj resursa ne zavisi od drugih testova
	private UUID citaonicaSaTriStola() throws Exception {
		String slug = "citaonica-" + UUID.randomUUID().toString().substring(0, 8);
		String tijelo = mockMvc.perform(post("/api/organizations")
				.header("Authorization", "Bearer " + emina)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Čitaonica FIT", "slug": "%s", "timezone": "Europe/Sarajevo"}
						""".formatted(slug)))
				.andReturn().getResponse().getContentAsString();
		UUID citaonica = UUID.fromString(JsonPath.read(tijelo, "$.id"));
		// Namjerno ne po abecedi, da se vidi da redoslijed dolazi iz upita, a ne iz redoslijeda upisa
		for (String sto : List.of("Sto 3", "Sto 1", "Sto 2")) {
			mockMvc.perform(post("/api/organizations/{id}/resources", citaonica)
					.header("Authorization", "Bearer " + emina)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"name": "%s", "slotMinutes": 120}
							""".formatted(sto)))
					.andExpect(status().isCreated());
		}
		return citaonica;
	}

	private ResultActions lista(UUID organizacija, String token, String upit) throws Exception {
		return mockMvc.perform(get("/api/organizations/{id}/resources?" + upit, organizacija)
				.header("Authorization", "Bearer " + token));
	}

	@Test
	void listaIdePoNazivuPoStranicamaISortIzZahtjevaSeIgnorise() throws Exception {
		UUID citaonica = citaonicaSaTriStola();

		lista(citaonica, emina, "page=0&size=2&sort=name,desc")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].name", contains("Sto 1", "Sto 2")))
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.totalPages").value(2));

		lista(citaonica, emina, "page=1&size=2")
				.andExpect(jsonPath("$.content[*].name", contains("Sto 3")))
				.andExpect(jsonPath("$.page").value(1));
	}

	@Test
	void stranicaNeMozeBitiVecaOdSto() throws Exception {
		lista(teretana, emina, "size=5000")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(100));
	}

	@Test
	void klijentVidiResurseSvojeOrganizacije() throws Exception {
		lista(teretana, dzenan, "").andExpect(status().isOk());
	}

	@Test
	void trajanjeTerminaDuzeOdOsamSatiVraca400ZaTajPolje() throws Exception {
		napravi(emina, """
				{"name": "Cjelodnevni kamp", "slotMinutes": 600}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.greske[0].polje").value("slotMinutes"));
	}

}
