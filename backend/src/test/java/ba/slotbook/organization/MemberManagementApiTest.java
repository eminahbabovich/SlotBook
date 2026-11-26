package ba.slotbook.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import ba.slotbook.TestcontainersConfiguration;

// Svaki test pravi svoju teretanu i svoje ljude (sufiks), da redoslijed testova ne utiče na brojanje admina
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class MemberManagementApiTest {

	private static final String LOZINKA = "bjelasnica-zimi-2026";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	private String sufiks;
	private String amel;
	private UUID teretana;

	@BeforeEach
	void amelJeJediniAdminNoveTeretane() throws Exception {
		sufiks = UUID.randomUUID().toString().substring(0, 8);
		amel = nalog("amel");
		teretana = organizacija(amel, "teretana-" + sufiks);
	}

	@Test
	void adminDajeKlijentuUloguProvideraIToIdeUDnevnik() throws Exception {
		nalog("nermina");
		UUID nermina = clan("nermina", "CLIENT");

		uloga(nermina, "PROVIDER", amel).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("PROVIDER"));

		assertThat(ulogaUBazi(nermina)).isEqualTo("PROVIDER");
		assertThat(jdbc.queryForObject("select detail from audit_log where organization_id = ? and action = "
				+ "'MEMBER_ROLE_CHANGED'", String.class, teretana)).isEqualTo("CLIENT -> PROVIDER");
	}

	@Test
	void jediniAdminSeNeSpustaNiUklanjaAKadImaDrugogMoze() throws Exception {
		UUID amelId = id("amel");

		uloga(amelId, "CLIENT", amel).andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Organizacija bi ostala bez admina"));
		posalji(delete("/api/organizations/{id}/members/{u}", teretana, amelId), amel, null)
				.andExpect(status().isConflict());

		nalog("edina");
		clan("edina", "ADMIN");
		uloga(amelId, "CLIENT", amel).andExpect(status().isOk());
		assertThat(ulogaUBazi(amelId)).isEqualTo("CLIENT");
	}

	@Test
	void clanKojiVodiResursSeNeUklanjaINeSpustaUKlijentaASpisakResursaStizeUOdgovoru() throws Exception {
		nalog("haris");
		UUID haris = clan("haris", "PROVIDER");
		UUID sala = resurs("Sala za boks");
		jdbc.update("update resources set provider_id = ? where id = ?", haris, sala);

		posalji(delete("/api/organizations/{id}/members/{u}", teretana, haris), amel, null)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.resursi", contains("Sala za boks")));
		uloga(haris, "CLIENT", amel).andExpect(status().isConflict());

		assertThat(ulogaUBazi(haris)).isEqualTo("PROVIDER");
		// Admin može: admin vodi resurse isto kao provider
		uloga(haris, "ADMIN", amel).andExpect(status().isOk());
	}

	@Test
	void uklonjenClanGubiBuduceRezervacijeIListuCekanjaAProsleOstaju() throws Exception {
		nalog("lamija");
		UUID lamija = clan("lamija", "CLIENT");
		UUID sala = resurs("Teretana gornji sprat");
		UUID buduca = rezervacija(sala, lamija, "2035-11-19T08:00:00Z", "CONFIRMED");
		UUID prosla = rezervacija(sala, lamija, "2025-11-19T08:00:00Z", "COMPLETED");
		jdbc.update("insert into waitlist_entries (id, resource_id, client_id, start_time, end_time, status, created_at) "
				+ "values (?, ?, ?, '2035-11-20T08:00:00Z', '2035-11-20T09:00:00Z', 'WAITING', now())",
				UUID.randomUUID(), sala, lamija);

		posalji(delete("/api/organizations/{id}/members/{u}", teretana, lamija), amel, null)
				.andExpect(status().isNoContent());

		assertThat(ulogaUBazi(lamija)).isNull();
		assertThat(statusRezervacije(buduca)).isEqualTo("CANCELLED");
		assertThat(statusRezervacije(prosla)).isEqualTo("COMPLETED");
		// Mail o otkazivanju ide Lamiji, jer je otkazao admin, a ne ona
		assertThat(jdbc.queryForObject("select count(*) from notifications where booking_id = ? and user_id = ? "
				+ "and type = 'BOOKING_CANCELLED'", Integer.class, buduca, lamija)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select status from waitlist_entries where client_id = ?", String.class, lamija))
				.isEqualTo("CANCELLED");
		assertThat(jdbc.queryForObject("select count(*) from audit_log where organization_id = ? and action = "
				+ "'MEMBER_REMOVED' and target_id = ?", Integer.class, teretana, lamija)).isEqualTo(1);
	}

	@Test
	void nepostojeciClanVraca404() throws Exception {
		posalji(delete("/api/organizations/{id}/members/{u}", teretana, UUID.randomUUID()), amel, null)
				.andExpect(status().isNotFound());
	}

	@Test
	void otvorenePozivniceSeVideIPovuceniMailNeOdlazi() throws Exception {
		String email = "dzenan-" + sufiks + "@example.com";
		posalji(post("/api/organizations/{id}/invites", teretana), amel, """
				{"email": "%s", "role": "CLIENT"}
				""".formatted(email)).andExpect(status().isCreated());

		String lista = posalji(get("/api/organizations/{id}/invites", teretana), amel, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].email").value(email))
				.andReturn().getResponse().getContentAsString();
		String inviteId = JsonPath.read(lista, "$.content[0].id");

		posalji(delete("/api/organizations/{id}/invites/{i}", teretana, inviteId), amel, null)
				.andExpect(status().isNoContent());

		assertThat(jdbc.queryForObject("select count(*) from organization_invites where organization_id = ?",
				Integer.class, teretana)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from notifications where email = ? and status = 'PENDING'",
				Integer.class, email)).isZero();
		posalji(delete("/api/organizations/{id}/invites/{i}", teretana, inviteId), amel, null)
				.andExpect(status().isNotFound());
	}

	// Adminu druge teretane @PreAuthorize ne pušta ni promjenu uloge ni uklanjanje
	@Test
	void adminDrugeOrganizacijeNeMijenjaNiNeUklanjaClanove() throws Exception {
		nalog("nermin");
		UUID nermin = clan("nermin", "CLIENT");
		String strani = nalog("strani");
		organizacija(strani, "druga-" + sufiks);

		uloga(nermin, "ADMIN", strani).andExpect(status().isForbidden());
		posalji(delete("/api/organizations/{id}/members/{u}", teretana, nermin), strani, null)
				.andExpect(status().isForbidden());

		assertThat(ulogaUBazi(nermin)).isEqualTo("CLIENT");
	}

	private ResultActions uloga(UUID clan, String uloga, String token) throws Exception {
		return posalji(put("/api/organizations/{id}/members/{u}/role", teretana, clan), token, """
				{"role": "%s"}
				""".formatted(uloga));
	}

	private ResultActions posalji(MockHttpServletRequestBuilder zahtjev, String token, String tijelo) throws Exception {
		zahtjev.header("Authorization", "Bearer " + token);
		if (tijelo != null) {
			zahtjev.contentType(MediaType.APPLICATION_JSON).content(tijelo);
		}
		return mockMvc.perform(zahtjev);
	}

	private String email(String ime) {
		return ime + "-" + sufiks + "@example.com";
	}

	private UUID id(String ime) {
		return jdbc.queryForObject("select id from users where email = ?", UUID.class, email(ime));
	}

	private String nalog(String ime) throws Exception {
		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "%s", "displayName": "%s"}
				""".formatted(email(ime), LOZINKA, ime))).andExpect(status().isAccepted());
		jdbc.update("update users set email_verified_at = now() where email = ?", email(ime));
		String tijelo = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "%s"}
				""".formatted(email(ime), LOZINKA))).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
		return JsonPath.read(tijelo, "$.accessToken");
	}

	private UUID organizacija(String token, String slug) throws Exception {
		String tijelo = posalji(post("/api/organizations"), token, """
				{"name": "Teretana %s", "slug": "%s", "timezone": "Europe/Sarajevo"}
				""".formatted(slug, slug)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(tijelo, "$.id"));
	}

	private UUID clan(String ime, String uloga) {
		jdbc.update("insert into organization_members select ?, id, ?, now() from users where email = ?", teretana,
				uloga, email(ime));
		return id(ime);
	}

	private UUID resurs(String naziv) throws Exception {
		String tijelo = posalji(post("/api/organizations/{id}/resources", teretana), amel, """
				{"name": "%s", "slotMinutes": 60}
				""".formatted(naziv)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(tijelo, "$.id"));
	}

	private UUID rezervacija(UUID resurs, UUID klijent, String pocetak, String status) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into bookings (id, resource_id, client_id, start_time, end_time, status, created_at) "
				+ "values (?, ?, ?, ?::timestamptz, ?::timestamptz + interval '1 hour', ?, now())", id, resurs, klijent,
				pocetak, pocetak, status);
		return id;
	}

	private String statusRezervacije(UUID id) {
		return jdbc.queryForObject("select status from bookings where id = ?", String.class, id);
	}

	private String ulogaUBazi(UUID clan) {
		return jdbc.query("select role from organization_members where organization_id = ? and user_id = ?",
				rs -> rs.next() ? rs.getString(1) : null, teretana, clan);
	}

}
