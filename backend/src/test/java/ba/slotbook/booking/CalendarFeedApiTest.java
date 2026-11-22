package ba.slotbook.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import ba.slotbook.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CalendarFeedApiTest {

	private static final String LOZINKA = "trebevic-u-magli-3";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	private String posalji(MockHttpServletRequestBuilder zahtjev, String token, String tijelo, int status)
			throws Exception {
		if (token != null) {
			zahtjev.header("Authorization", "Bearer " + token);
		}
		if (tijelo != null) {
			zahtjev.contentType(MediaType.APPLICATION_JSON).content(tijelo);
		}
		return mockMvc.perform(zahtjev).andExpect(status().is(status)).andReturn().getResponse()
				.getContentAsString(StandardCharsets.UTF_8);
	}

	private String nalog(String email) throws Exception {
		posalji(post("/api/auth/register"), null, """
				{"email": "%s", "password": "%s", "displayName": "Korisnik"}
				""".formatted(email, LOZINKA), 202);
		jdbc.update("update users set email_verified_at = now() where email = ?", email);
		return JsonPath.read(posalji(post("/api/auth/login"), null, """
				{"email": "%s", "password": "%s"}
				""".formatted(email, LOZINKA), 200), "$.accessToken");
	}

	private UUID resurs(String token, String slug) throws Exception {
		String organizacija = JsonPath.read(posalji(post("/api/organizations"), token, """
				{"name": "Čitaonica PMF", "slug": "%s", "timezone": "Europe/Sarajevo"}
				""".formatted(slug), 201), "$.id");
		return UUID.fromString(JsonPath.read(posalji(post("/api/organizations/{id}/resources", organizacija), token,
				"""
						{"name": "Sto 4, tihi dio", "slotMinutes": 60}
						""", 201), "$.id"));
	}

	private UUID rezervacija(UUID resurs, String email, String pocetak, String status) {
		UUID id = UUID.randomUUID();
		// Na čekanju mora imati rok potvrde (constraint bookings_pending_ima_rok), ovdje sat prije početka
		jdbc.update("insert into bookings (id, resource_id, client_id, start_time, end_time, status, created_at, "
				+ "expires_at) select ?, ?, id, ?::timestamptz, ?::timestamptz + interval '1 hour', ?, now(), "
				+ "case when ? = 'PENDING' then ?::timestamptz - interval '1 hour' end from users where email = ?",
				id, resurs, pocetak, pocetak, status, status, pocetak, email);
		return id;
	}

	private String noviLink(String token) throws Exception {
		return JsonPath.read(posalji(post("/api/me/calendar-feed"), token, null, 201), "$.token");
	}

	private String kalendar(String linkToken, int status) throws Exception {
		return posalji(get("/api/me/calendar.ics").param("token", linkToken), null, null, status);
	}

	@Test
	void linkBezPrijaveDajeMojeTermineBezTudjihOtkazanihIStarih() throws Exception {
		String lejla = nalog("lejla.ical@example.com");
		nalog("tarik.ical@example.com");
		UUID sto = resurs(lejla, "citaonica-pmf-ical");
		UUID potvrdjena = rezervacija(sto, "lejla.ical@example.com", "2035-11-19T08:00:00Z", "CONFIRMED");
		UUID naCekanju = rezervacija(sto, "lejla.ical@example.com", "2035-11-20T08:00:00Z", "PENDING");
		UUID otkazana = rezervacija(sto, "lejla.ical@example.com", "2035-11-21T08:00:00Z", "CANCELLED");
		UUID stara = rezervacija(sto, "lejla.ical@example.com", "2025-11-19T08:00:00Z", "COMPLETED");
		UUID tarikova = rezervacija(sto, "tarik.ical@example.com", "2035-11-19T10:00:00Z", "CONFIRMED");
		String link = noviLink(lejla);

		// Bez Authorization zaglavlja: Google kalendar ga nema
		mockMvc.perform(get("/api/me/calendar.ics").param("token", link))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("text/calendar"));
		String ics = kalendar(link, 200);

		assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n")
				.contains("UID:" + potvrdjena + "@slotbook\r\n", "DTSTART:20351119T080000Z\r\n",
						"SUMMARY:Sto 4\\, tihi dio\r\n", "LOCATION:Čitaonica PMF\r\n")
				.doesNotContain(otkazana.toString(), stara.toString(), tarikova.toString());
		// Na čekanju je u kalendaru, ali kao neodlučena
		String dogadjajNaCekanju = ics.substring(ics.indexOf("UID:" + naCekanju));
		assertThat(dogadjajNaCekanju.substring(0, dogadjajNaCekanju.indexOf("END:VEVENT")))
				.contains("STATUS:TENTATIVE");
		assertThat(ics.split("BEGIN:VEVENT", -1)).hasSize(3);
	}

	@Test
	void noviLinkGasiStariAUgasenLinkVise404() throws Exception {
		String amra = nalog("amra.ical@example.com");
		posalji(get("/api/me/calendar-feed"), amra, null, 200);
		mockMvc.perform(get("/api/me/calendar-feed").header("Authorization", "Bearer " + amra))
				.andExpect(jsonPath("$.active").value(false));

		String prvi = noviLink(amra);
		String drugi = noviLink(amra);

		kalendar(prvi, 404);
		kalendar(drugi, 200);
		// Token se ne može ponovo pročitati, baza ga nema
		mockMvc.perform(get("/api/me/calendar-feed").header("Authorization", "Bearer " + amra))
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.token").doesNotExist());
		assertThat(jdbc.queryForObject("select count(*) from calendar_feeds where token_hash = ? or token_hash = ?",
				Long.class, drugi, prvi)).isZero();

		posalji(delete("/api/me/calendar-feed"), amra, null, 204);
		kalendar(drugi, 404);
	}

	@Test
	void bezTokenaIliSIzmisljenimJe404SPorukomNaBosanskom() throws Exception {
		posalji(get("/api/me/calendar.ics"), null, null, 404);
		mockMvc.perform(get("/api/me/calendar.ics").param("token", "izmisljen"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Link za kalendar ne postoji"));
	}

	@Test
	void bezPrijaveSeLinkNeMozeNapravitiNiVidjeti() throws Exception {
		posalji(post("/api/me/calendar-feed"), null, null, 401);
		posalji(get("/api/me/calendar-feed"), null, null, 401);
		posalji(delete("/api/me/calendar-feed"), null, null, 401);
	}

}
