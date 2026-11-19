package ba.slotbook.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.springframework.transaction.IllegalTransactionStateException;

import com.jayway.jsonpath.JsonPath;

import ba.slotbook.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuditLogApiTest {

	private static final String LOZINKA = "igman-1984";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	AuditLogService auditLog;

	private String dzenana;
	private String mirza;
	private String emina;
	private UUID teretana;

	// Svaki test dobija svoju teretanu, jer se baza dijeli između testova bez rollbacka
	@BeforeEach
	void dzenanaOtvaraTeretanu() throws Exception {
		dzenana = token("dzenana.audit@example.com", "Dženana");
		mirza = token("mirza.audit@example.com", "Mirza");
		emina = token("emina.audit@example.com", "Emina");
		teretana = organizacija(dzenana, "teretana-audit-" + UUID.randomUUID().toString().substring(0, 8));
	}

	private String token(String email, String ime) throws Exception {
		if (jdbc.queryForObject("select count(*) from users where email = ?", Long.class, email) == 0) {
			posalji(post("/api/auth/register"), null, """
					{"email": "%s", "password": "%s", "displayName": "%s"}
					""".formatted(email, LOZINKA, ime)).andExpect(status().isAccepted());
			jdbc.update("update users set email_verified_at = now() where email = ?", email);
		}
		String tijelo = posalji(post("/api/auth/login"), null, """
				{"email": "%s", "password": "%s"}
				""".formatted(email, LOZINKA)).andReturn().getResponse().getContentAsString();
		return JsonPath.read(tijelo, "$.accessToken");
	}

	private UUID organizacija(String token, String slug) throws Exception {
		String tijelo = posalji(post("/api/organizations"), token, """
				{"name": "Teretana na Grbavici", "slug": "%s", "timezone": "Europe/Sarajevo"}
				""".formatted(slug)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(tijelo, "$.id"));
	}

	private ResultActions posalji(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder zahtjev,
			String token, String tijelo) throws Exception {
		if (token != null) {
			zahtjev.header("Authorization", "Bearer " + token);
		}
		if (tijelo != null) {
			zahtjev.contentType(MediaType.APPLICATION_JSON).content(tijelo);
		}
		return mockMvc.perform(zahtjev);
	}

	private ResultActions dnevnik(UUID organizacija, String token) throws Exception {
		return posalji(get("/api/organizations/{id}/audit-log", organizacija), token, null);
	}

	private UUID id(String email) {
		return jdbc.queryForObject("select id from users where email = ?", UUID.class, email);
	}

	private void clan(UUID organizacija, String email, String uloga) {
		jdbc.update("insert into organization_members select ?, id, ?, now() from users where email = ?",
				organizacija, uloga, email);
	}

	private long zapisa(UUID organizacija) {
		return jdbc.queryForObject("select count(*) from audit_log where organization_id = ?", Long.class,
				organizacija);
	}

	// Pozivnica ide kroz pravi mail, jer se token nigdje drugo ne vidi
	private String tokenPozivnice(String email) {
		String tijelo = jdbc.queryForObject("select body from notifications where email = ? and type = "
				+ "'ORGANIZATION_INVITE' and status = 'PENDING' order by created_at desc limit 1", String.class, email);
		Matcher link = Pattern.compile("pozivnica#token=([A-Za-z0-9_-]+)").matcher(tijelo);
		assertThat(link.find()).isTrue();
		return link.group(1);
	}

	@Test
	void adminVidiSvakuOsjetljivuRadnjuIKoJuJeUradioNajnovijuPrvu() throws Exception {
		posalji(post("/api/organizations/{id}/invites", teretana), dzenana, """
				{"email": "mirza.audit@example.com", "role": "PROVIDER"}
				""").andExpect(status().isCreated());
		posalji(post("/api/invites/accept"), mirza, """
				{"token": "%s"}
				""".formatted(tokenPozivnice("mirza.audit@example.com"))).andExpect(status().isOk());
		String tijelo = posalji(post("/api/organizations/{id}/resources", teretana), dzenana, """
				{"name": "Sala za grupne treninge", "slotMinutes": 60}
				""").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		UUID sala = UUID.fromString(JsonPath.read(tijelo, "$.id"));
		posalji(put("/api/organizations/{id}/resources/{r}", teretana, sala), dzenana, """
				{"name": "Sala 2, grupni treninzi", "slotMinutes": 90}
				""").andExpect(status().isOk());
		posalji(put("/api/organizations/{id}/resources/{r}/provider", teretana, sala), dzenana, """
				{"providerId": "%s"}
				""".formatted(id("mirza.audit@example.com"))).andExpect(status().isOk());
		posalji(delete("/api/organizations/{id}/resources/{r}", teretana, sala), mirza, null)
				.andExpect(status().isNoContent());

		dnevnik(teretana, dzenana)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(7))
				.andExpect(jsonPath("$.content[*].action", contains("RESOURCE_DEACTIVATED",
						"RESOURCE_PROVIDER_CHANGED", "RESOURCE_UPDATED", "RESOURCE_CREATED", "INVITE_ACCEPTED",
						"INVITE_SENT", "ORGANIZATION_CREATED")))
				.andExpect(jsonPath("$.content[*].actorName", contains("Mirza", "Dženana", "Dženana", "Dženana",
						"Mirza", "Dženana", "Dženana")))
				.andExpect(jsonPath("$.content[0].targetId").value(sala.toString()))
				.andExpect(jsonPath("$.content[0].detail").value("Sala 2, grupni treninzi"))
				.andExpect(jsonPath("$.content[1].detail").value(id("mirza.audit@example.com").toString()))
				.andExpect(jsonPath("$.content[4].actorId").value(id("mirza.audit@example.com").toString()))
				.andExpect(jsonPath("$.content[4].detail").value("PROVIDER"))
				.andExpect(jsonPath("$.content[5].detail").value("PROVIDER"))
				.andExpect(jsonPath("$.content[6].targetId").value(teretana.toString()))
				.andExpect(jsonPath("$.content[0].createdAt").isString())
				// Email pozvane osobe ne ulazi u dnevnik, vidi docs/ODLUKE.md
				.andExpect(content().string(not(containsString("@example.com"))));
	}

	@Test
	void providerIKlijentNeCitajuDnevnikSvojeOrganizacije() throws Exception {
		clan(teretana, "mirza.audit@example.com", "PROVIDER");
		clan(teretana, "emina.audit@example.com", "CLIENT");

		dnevnik(teretana, mirza).andExpect(status().isForbidden());
		dnevnik(teretana, emina).andExpect(status().isForbidden());
	}

	@Test
	void adminDrugeOrganizacijeDobija403IVidiSamoSvojeZapise() throws Exception {
		UUID eminina = organizacija(emina, "citaonica-audit-" + UUID.randomUUID().toString().substring(0, 8));

		dnevnik(teretana, emina).andExpect(status().isForbidden());
		dnevnik(eminina, emina)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].targetId").value(eminina.toString()));
	}

	@Test
	void odbijenaRadnjaNeOstavljaZapis() throws Exception {
		posalji(post("/api/organizations/{id}/resources", teretana), dzenana, """
				{"name": "Sauna", "slotMinutes": 30}
				""").andExpect(status().isCreated());
		long prije = zapisa(teretana);
		UUID sauna = jdbc.queryForObject("select id from resources where organization_id = ?", UUID.class, teretana);

		// Emina nije član, pa ne može voditi resurs
		posalji(put("/api/organizations/{id}/resources/{r}/provider", teretana, sauna), dzenana, """
				{"providerId": "%s"}
				""".formatted(id("emina.audit@example.com"))).andExpect(status().isBadRequest());
		posalji(post("/api/organizations/{id}/invites", teretana), mirza, """
				{"email": "neko.audit@example.com", "role": "ADMIN"}
				""").andExpect(status().isForbidden());

		assertThat(zapisa(teretana)).isEqualTo(prije);
	}

	// Zapis ide samo u transakciji same radnje, pa se s njom i poništi. Sam za sebe ne može nastati.
	@Test
	void zapisVanTransakcijeRadnjeNijeMoguc() {
		assertThatThrownBy(() -> auditLog.record(teretana, id("dzenana.audit@example.com"),
				AuditAction.RESOURCE_CREATED, UUID.randomUUID(), "Sala"))
				.isInstanceOf(IllegalTransactionStateException.class);
		assertThat(zapisa(teretana)).isEqualTo(1);
	}

}
