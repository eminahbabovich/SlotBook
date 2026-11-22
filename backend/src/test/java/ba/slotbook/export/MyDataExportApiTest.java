package ba.slotbook.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
class MyDataExportApiTest {

	private static final String LOZINKA = "vrelo-bosne-7";
	private static final String LEJLA = "lejla.izvoz@example.com";
	private static final String TARIK = "tarik.izvoz@example.com";

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
				.getContentAsString();
	}

	private String nalog(String email, String ime) throws Exception {
		posalji(post("/api/auth/register"), null, """
				{"email": "%s", "password": "%s", "displayName": "%s"}
				""".formatted(email, LOZINKA, ime), 202);
		jdbc.update("update users set email_verified_at = now() where email = ?", email);
		return JsonPath.read(posalji(post("/api/auth/login"), null, """
				{"email": "%s", "password": "%s"}
				""".formatted(email, LOZINKA), 200), "$.accessToken");
	}

	private UUID organizacija(String token, String naziv, String slug) throws Exception {
		return UUID.fromString(JsonPath.read(posalji(post("/api/organizations"), token, """
				{"name": "%s", "slug": "%s", "timezone": "Europe/Sarajevo"}
				""".formatted(naziv, slug), 201), "$.id"));
	}

	private UUID rezervacija(UUID resurs, String email, String pocetak, String kraj) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into bookings (id, resource_id, client_id, start_time, end_time, status, created_at) "
				+ "select ?, ?, id, ?::timestamptz, ?::timestamptz, 'CONFIRMED', now() from users where email = ?",
				id, resurs, pocetak, kraj, email);
		jdbc.update("insert into booking_events (id, booking_id, from_status, to_status, actor_id, created_at) "
				+ "select ?, ?, null, 'CONFIRMED', id, now() from users where email = ?", UUID.randomUUID(), id, email);
		return id;
	}

	private void naListi(UUID resurs, String email, String pocetak, String kraj) {
		jdbc.update("insert into waitlist_entries (id, resource_id, client_id, start_time, end_time, status, "
				+ "created_at) select gen_random_uuid(), ?, id, ?::timestamptz, ?::timestamptz, 'WAITING', now() "
				+ "from users where email = ?", resurs, pocetak, kraj, email);
	}

	@Test
	void izvozImaSveOKorisnikuBezHashevaTokenaITudjihPodataka() throws Exception {
		String lejla = nalog(LEJLA, "Lejla");
		String tarik = nalog(TARIK, "Tarik");
		posalji(post("/api/auth/login"), null, """
				{"email": "%s", "password": "pogresna-lozinka"}
				""".formatted(LEJLA), 401);
		UUID citaonica = organizacija(lejla, "Čitaonica PMF", "citaonica-pmf-izvoz");
		UUID sto = UUID.fromString(JsonPath.read(posalji(post("/api/organizations/{id}/resources", citaonica), lejla,
				"""
						{"name": "Sto 4 kod prozora", "slotMinutes": 60}
						""", 201), "$.id"));
		UUID teretana = organizacija(tarik, "Teretana Ilidža", "teretana-ilidza-izvoz");
		posalji(post("/api/organizations/{id}/invites", teretana), tarik, """
				{"email": "%s", "role": "CLIENT"}
				""".formatted(LEJLA), 201);
		// Tuđa pozivnica i tuđi zapis u dnevniku, da se vidi kad upit izgubi uslov
		posalji(post("/api/organizations/{id}/invites", teretana), tarik, """
				{"email": "amra.izvoz@example.com", "role": "PROVIDER"}
				""", 201);
		UUID lejlina = rezervacija(sto, LEJLA, "2035-11-19T08:00:00Z", "2035-11-19T09:00:00Z");
		UUID tarikova = rezervacija(sto, TARIK, "2035-11-19T10:00:00Z", "2035-11-19T11:00:00Z");
		// Svako čeka na tuđi termin. Tarikov upis ne smije ući u Lejlin izvoz.
		naListi(sto, LEJLA, "2035-11-19T10:00:00Z", "2035-11-19T11:00:00Z");
		naListi(sto, TARIK, "2035-11-19T08:00:00Z", "2035-11-19T09:00:00Z");

		String izvoz = posalji(get("/api/me/export"), lejla, null, 200);

		mockMvc.perform(get("/api/me/export").header("Authorization", "Bearer " + lejla))
				.andExpect(jsonPath("$.user.email").value(LEJLA))
				.andExpect(jsonPath("$.user.displayName").value("Lejla"))
				.andExpect(jsonPath("$.user.createdAt", endsWith("Z")))
				.andExpect(jsonPath("$.user.emailVerifiedAt").isString())
				.andExpect(jsonPath("$.exportedAt", endsWith("Z")))
				.andExpect(jsonPath("$.memberships[*].organizationName", contains("Čitaonica PMF")))
				.andExpect(jsonPath("$.memberships[0].role").value("ADMIN"))
				.andExpect(jsonPath("$.bookings[*].id", contains(lejlina.toString())))
				.andExpect(jsonPath("$.bookings[0].resourceName").value("Sto 4 kod prozora"))
				.andExpect(jsonPath("$.bookings[0].organizationName").value("Čitaonica PMF"))
				.andExpect(jsonPath("$.bookings[0].startTime").value("2035-11-19T08:00:00Z"))
				.andExpect(jsonPath("$.bookings[0].status").value("CONFIRMED"))
				.andExpect(jsonPath("$.bookingEvents[*].bookingId", contains(lejlina.toString())))
				.andExpect(jsonPath("$.bookingEvents[0].byMe").value(true))
				.andExpect(jsonPath("$.managedResources[*].name", contains("Sto 4 kod prozora")))
				.andExpect(jsonPath("$.notifications[*].type", hasItems("EMAIL_VERIFICATION", "ORGANIZATION_INVITE")))
				.andExpect(jsonPath("$.notifications[0].subject").isString())
				.andExpect(jsonPath("$.sessions.length()").value(1))
				.andExpect(jsonPath("$.emailLinks[*].type", contains("EMAIL_VERIFY")))
				.andExpect(jsonPath("$.invites[*].organizationName", contains("Teretana Ilidža")))
				.andExpect(jsonPath("$.invites[0].role").value("CLIENT"))
				.andExpect(jsonPath("$.loginFailures.failures").value(1))
				.andExpect(jsonPath("$.auditEntries[*].action", hasItems("ORGANIZATION_CREATED", "RESOURCE_CREATED")))
				.andExpect(jsonPath("$.auditEntries[*].organizationName", everyItem(is("Čitaonica PMF"))))
				.andExpect(jsonPath("$.waitlistEntries[*].startTime", contains("2035-11-19T10:00:00Z")))
				.andExpect(jsonPath("$.waitlistEntries[0].resourceName").value("Sto 4 kod prozora"))
				.andExpect(jsonPath("$.waitlistEntries[0].status").value("WAITING"));

		// Ništa od ovoga ne smije izaći: hash lozinke, hashevi tokena (u bazi ih ima i za Lejlu), tijela
		// mailova (u njima su linkovi s pravim tokenima) i Tarikovi podaci
		List<String> tajne = jdbc.queryForList("""
				select password_hash from users where email = :e
				union all select t.token_hash from refresh_tokens t join users u on u.id = t.user_id where u.email = :e
				union all select t.token_hash from user_tokens t join users u on u.id = t.user_id where u.email = :e
				union all select token_hash from organization_invites where email = :e
				""".replace(":e", "'" + LEJLA + "'"), String.class);
		assertThat(tajne).hasSize(4);
		for (String tajna : tajne) {
			assertThat(izvoz).doesNotContain(tajna);
		}
		assertThat(izvoz).doesNotContain("$2a$").doesNotContain("token=").doesNotContain("Zdravo")
				.doesNotContain(TARIK).doesNotContain(tarikova.toString()).doesNotContain("Tarik");
	}

	@Test
	void bezPrijaveNemaIzvoza() throws Exception {
		posalji(get("/api/me/export"), null, null, 401);
	}

}
