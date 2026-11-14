package ba.slotbook.booking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.user.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class BookingApiTest {

	private static final String LOZINKA = "stari-most-1566";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	private String amila;
	private String hana;
	private String lamija;
	private UUID citaonica;

	// Amila je admin čitaonice, Hana i Lamija studentice (CLIENT). Baza se dijeli između testova,
	// pa svaki test pravi svoj sto, a korisnici i organizacija se prave samo jednom.
	@BeforeEach
	void citaonicaSaDvijeStudentice() throws Exception {
		amila = token("amila.rezervacije@example.com", "Amila");
		hana = token("hana.rezervacije@example.com", "Hana");
		lamija = token("lamija.rezervacije@example.com", "Lamija");
		var postojeca = jdbc.queryForList("select id from organizations where slug = 'citaonica-rezervacije-api'",
				UUID.class);
		citaonica = postojeca.isEmpty() ? novaOrganizacija() : postojeca.getFirst();
		for (String email : new String[] { "hana.rezervacije@example.com", "lamija.rezervacije@example.com" }) {
			jdbc.update("insert into organization_members values (?, ?, 'CLIENT', now()) on conflict do nothing",
					citaonica, users.findByEmail(email).orElseThrow().getId());
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

	private UUID novaOrganizacija() throws Exception {
		return idIz(mockMvc.perform(post("/api/organizations")
				.header("Authorization", "Bearer " + amila)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Čitaonica FIT", "slug": "citaonica-rezervacije-api", "timezone": "Europe/Sarajevo"}
						""")));
	}

	private UUID idIz(ResultActions odgovor) throws Exception {
		return UUID.fromString(JsonPath.read(odgovor.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), "$.id"));
	}

	// Sto sa satnim terminima, ponedjeljkom od 08 do 12
	private UUID sto() throws Exception {
		UUID sto = idIz(mockMvc.perform(post("/api/organizations/{id}/resources", citaonica)
				.header("Authorization", "Bearer " + amila)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Sto %s", "slotMinutes": 60}
						""".formatted(UUID.randomUUID().toString().substring(0, 8)))));
		mockMvc.perform(put("/api/resources/{id}/availability", sto)
				.header("Authorization", "Bearer " + amila)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"rules": [{"dayOfWeek": "MONDAY", "start": "08:00", "end": "12:00"}]}
						"""))
				.andExpect(status().isOk());
		return sto;
	}

	private ResultActions rezervisi(String token, UUID resurs, String pocetak) throws Exception {
		return mockMvc.perform(post("/api/resources/{id}/bookings", resurs)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"startTime": "%s"}
						""".formatted(pocetak)));
	}

	@Test
	void studenticaRezervisePonudjenTerminIDobijaGaUUtc() throws Exception {
		rezervisi(hana, sto(), "2035-11-19T09:00:00+01:00")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.startTime").value("2035-11-19T08:00:00Z"))
				.andExpect(jsonPath("$.endTime").value("2035-11-19T09:00:00Z"))
				.andExpect(jsonPath("$.status").value("CONFIRMED"));
	}

	@Test
	void drugaStudenticaZaIstiTerminDobija409() throws Exception {
		UUID sto = sto();
		rezervisi(hana, sto, "2035-11-19T09:00:00+01:00").andExpect(status().isCreated());

		// Isti trenutak zapisan u UTC-u
		rezervisi(lamija, sto, "2035-11-19T08:00:00Z")
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.title").value("Termin je zauzet"));
	}

	@Test
	void terminKojiSeNeNudiDaje400ZaPoljeStartTime() throws Exception {
		rezervisi(hana, sto(), "2035-11-19T09:30:00+01:00")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.greske[0].polje").value("startTime"));
	}

	@Test
	void pocetakBezPomakaSeOdbija() throws Exception {
		rezervisi(hana, sto(), "2035-11-19T09:00:00")
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"));
	}

	// Klijent ne vidi deaktiviran resurs, pa ga ne može ni rezervisati
	@Test
	void klijentNeMozeRezervisatiDeaktiviranResurs() throws Exception {
		UUID sto = sto();
		mockMvc.perform(delete("/api/organizations/{id}/resources/{resourceId}", citaonica, sto)
				.header("Authorization", "Bearer " + amila))
				.andExpect(status().isNoContent());

		rezervisi(hana, sto, "2035-11-19T09:00:00+01:00").andExpect(status().isForbidden());
	}

}
