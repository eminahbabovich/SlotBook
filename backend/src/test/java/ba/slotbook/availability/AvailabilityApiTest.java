package ba.slotbook.availability;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.user.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AvailabilityApiTest {

	private static final String LOZINKA = "sebilj-1891-vrijeme";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	private String selma;
	private String haris;
	private String nedim;
	private UUID teretana;

	// Selma je admin teretane, Haris trener, Nedim klijent
	@BeforeEach
	void teretana() throws Exception {
		selma = token("selma.vrijeme@example.com", "Selma");
		haris = token("haris.vrijeme@example.com", "Haris");
		nedim = token("nedim.vrijeme@example.com", "Nedim");

		var postojeca = jdbc.queryForList("select id from organizations where slug = 'teretana-vrijeme'", UUID.class);
		if (postojeca.isEmpty()) {
			String tijelo = mockMvc.perform(post("/api/organizations")
					.header("Authorization", "Bearer " + selma)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"name": "Teretana Otoka", "slug": "teretana-vrijeme", "timezone": "Europe/Sarajevo"}
							"""))
					.andReturn().getResponse().getContentAsString();
			teretana = UUID.fromString(JsonPath.read(tijelo, "$.id"));
		} else {
			teretana = postojeca.getFirst();
		}
		clan("haris.vrijeme@example.com", "PROVIDER");
		clan("nedim.vrijeme@example.com", "CLIENT");
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

	private void clan(String email, String uloga) {
		jdbc.update("insert into organization_members values (?, ?, ?, now()) on conflict do nothing", teretana,
				users.findByEmail(email).orElseThrow().getId(), uloga);
	}

	private UUID resurs(String token, String naziv) throws Exception {
		String tijelo = mockMvc.perform(post("/api/organizations/{id}/resources", teretana)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "%s", "slotMinutes": 60}
						""".formatted(naziv)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(tijelo, "$.id"));
	}

	private ResultActions postavi(String token, UUID resurs, String pravila) throws Exception {
		return mockMvc.perform(put("/api/resources/{resourceId}/availability", resurs)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"rules\": [" + pravila + "]}"));
	}

	private ResultActions procitaj(String token, UUID resurs) throws Exception {
		return mockMvc.perform(get("/api/resources/{resourceId}/availability", resurs)
				.header("Authorization", "Bearer " + token));
	}

	// Poslano namjerno izmiješano, a vraća se po danu pa po početku
	@Test
	void adminPostavljaSedmicuAKlijentJeVidiUZoniOrganizacije() throws Exception {
		UUID sala = resurs(selma, "Sala za funkcionalni");

		postavi(selma, sala, """
				{"dayOfWeek": "SATURDAY", "start": "09:00", "end": "13:00"},
				{"dayOfWeek": "MONDAY", "start": "13:00", "end": "20:00"},
				{"dayOfWeek": "MONDAY", "start": "08:00", "end": "12:00"}
				""").andExpect(status().isOk());

		procitaj(nedim, sala)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.timezone").value("Europe/Sarajevo"))
				.andExpect(jsonPath("$.rules[*].dayOfWeek", contains("MONDAY", "MONDAY", "SATURDAY")))
				.andExpect(jsonPath("$.rules[*].start", contains("08:00:00", "13:00:00", "09:00:00")));
	}

	@Test
	void noviPutZamjenjujeCijeluSedmicu() throws Exception {
		UUID sauna = resurs(selma, "Sauna");
		postavi(selma, sauna, """
				{"dayOfWeek": "MONDAY", "start": "08:00", "end": "12:00"},
				{"dayOfWeek": "TUESDAY", "start": "08:00", "end": "12:00"}
				""").andExpect(status().isOk());

		postavi(selma, sauna, """
				{"dayOfWeek": "FRIDAY", "start": "16:00", "end": "22:00"}
				""").andExpect(status().isOk());

		procitaj(selma, sauna).andExpect(jsonPath("$.rules[*].dayOfWeek", contains("FRIDAY")));
	}

	@Test
	void trenerPostavljaSvojeRadnoVrijemeAliNeTudje() throws Exception {
		UUID svoj = resurs(haris, "Personalni trening s Harisom");
		UUID tudji = resurs(selma, "Sala za boks");

		postavi(haris, svoj, """
				{"dayOfWeek": "WEDNESDAY", "start": "17:00", "end": "21:00"}
				""").andExpect(status().isOk());
		postavi(haris, tudji, """
				{"dayOfWeek": "WEDNESDAY", "start": "17:00", "end": "21:00"}
				""").andExpect(status().isForbidden());
		postavi(nedim, svoj, "").andExpect(status().isForbidden());
	}

	@Test
	void preklapanjeIKrajPrijePocetkaVracaju400SPoljem() throws Exception {
		UUID sala = resurs(selma, "Sala za jogu");

		postavi(selma, sala, """
				{"dayOfWeek": "MONDAY", "start": "08:00", "end": "12:00"},
				{"dayOfWeek": "MONDAY", "start": "11:00", "end": "14:00"}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.greske[0].polje").value("rules[1]"));
		postavi(selma, sala, """
				{"dayOfWeek": "FRIDAY", "start": "22:00", "end": "02:00"}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.greske[0].polje").value("rules[0].end"));
		postavi(selma, sala, """
				{"dayOfWeek": "FRIDAY", "end": "12:00"}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.greske[0].polje").value("rules[0].start"));
	}

	// Odbijen zahtjev ne smije obrisati staru sedmicu. Brisanje i upis su u istoj transakciji, pa ih greška vrati oba.
	@Test
	void odbijenaIzmjenaOstavljaStaroRadnoVrijeme() throws Exception {
		UUID sala = resurs(selma, "Sala za spinning");
		postavi(selma, sala, """
				{"dayOfWeek": "THURSDAY", "start": "07:00", "end": "10:00"}
				""").andExpect(status().isOk());

		postavi(selma, sala, """
				{"dayOfWeek": "MONDAY", "start": "08:00", "end": "12:00"},
				{"dayOfWeek": "MONDAY", "start": "09:00", "end": "10:00"}
				""").andExpect(status().isBadRequest());

		procitaj(selma, sala).andExpect(jsonPath("$.rules[*].dayOfWeek", contains("THURSDAY")));
	}

}
