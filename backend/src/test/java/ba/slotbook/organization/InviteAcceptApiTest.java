package ba.slotbook.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.jayway.jsonpath.JsonPath;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.auth.RandomToken;
import ba.slotbook.user.User;
import ba.slotbook.user.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class InviteAcceptApiTest {

	private static final String LOZINKA = "vrelo-bosne-1888";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository users;

	@Autowired
	OrganizationRepository organizations;

	@Autowired
	JdbcTemplate jdbc;

	private String admin;
	private UUID teretana;

	// Baza se dijeli između testova bez rollbacka, pa svaki test poziva svoju osobu
	@BeforeEach
	void jasminJeAdminTeretane() throws Exception {
		admin = token("jasmin.prihvat@example.com", "Jasmin");
		var postojeca = organizations.findBySlug("teretana-prihvat");
		if (postojeca.isPresent()) {
			teretana = postojeca.get().getId();
			return;
		}
		String tijelo = mockMvc.perform(post("/api/organizations")
				.header("Authorization", "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Teretana Ilidža", "slug": "teretana-prihvat", "timezone": "Europe/Sarajevo"}
						"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		teretana = UUID.fromString(JsonPath.read(tijelo, "$.id"));
	}

	private String token(String email, String ime) throws Exception {
		if (users.findByEmail(email).isEmpty()) {
			mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
					{"email": "%s", "password": "%s", "displayName": "%s"}
					""".formatted(email, LOZINKA, ime))).andExpect(status().isCreated());
			jdbc.update("update users set email_verified_at = now() where email = lower(?)", email);
		}
		String tijelo = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "%s"}
				""".formatted(email, LOZINKA))).andReturn().getResponse().getContentAsString();
		return JsonPath.read(tijelo, "$.accessToken");
	}

	private String pozovi(String email, String uloga) throws Exception {
		mockMvc.perform(post("/api/organizations/{id}/invites", teretana)
				.header("Authorization", "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "%s", "role": "%s"}
						""".formatted(email, uloga)))
				.andExpect(status().isCreated());
		return tokenIzMaila(User.normalizeEmail(email));
	}

	// Token postoji samo u mailu koji čeka slanje, admin ga ne dobija
	private String tokenIzMaila(String email) {
		String tijelo = jdbc.queryForObject("select body from notifications where email = ? and type = "
				+ "'ORGANIZATION_INVITE' and status = 'PENDING' order by created_at desc limit 1", String.class, email);
		Matcher link = Pattern.compile("http://localhost:5173/pozivnica#token=([A-Za-z0-9_-]+)").matcher(tijelo);
		assertThat(link.find()).as("link u mailu: %s", tijelo).isTrue();
		return link.group(1);
	}

	private ResultActions prihvati(String accessToken, String tokenPozivnice) throws Exception {
		return mockMvc.perform(post("/api/invites/accept")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"token": "%s"}
						""".formatted(tokenPozivnice)));
	}

	@Test
	void pozvanaOsobaPostajeClanSUlogomIzPozivnice() throws Exception {
		String pozivnica = pozovi("irma.prihvat@example.com", "PROVIDER");
		String irma = token("irma.prihvat@example.com", "Irma");

		prihvati(irma, pozivnica)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.slug").value("teretana-prihvat"))
				.andExpect(jsonPath("$.role").value("PROVIDER"));

		mockMvc.perform(get("/api/organizations/{id}/members", teretana).header("Authorization", "Bearer " + admin))
				.andExpect(jsonPath("$.content[?(@.displayName == 'Irma')].role", hasItem("PROVIDER")));
	}

	@Test
	void istaPozivnicaSeNeMozeIskoristitiDvaput() throws Exception {
		String pozivnica = pozovi("haris.prihvat@example.com", "CLIENT");
		String haris = token("haris.prihvat@example.com", "Haris");
		prihvati(haris, pozivnica).andExpect(status().isOk());

		prihvati(haris, pozivnica)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("Ova pozivnica je već iskorištena."));
	}

	@Test
	void pozivnicuNaTudjiEmailNijeMoguceIskoristiti() throws Exception {
		String pozivnica = pozovi("melisa.prihvat@example.com", "ADMIN");
		String drugi = token("adnan.prihvat@example.com", "Adnan");

		prihvati(drugi, pozivnica).andExpect(status().isForbidden());
	}

	@Test
	void isteklaPozivnicaVraca410() throws Exception {
		String pozivnica = RandomToken.generate();
		jdbc.update("""
				insert into organization_invites (id, organization_id, email, role, token_hash, created_at, expires_at)
				values (gen_random_uuid(), ?, 'nedim.prihvat@example.com', 'CLIENT', ?,
				        now() - interval '8 days', now() - interval '1 day')""",
				teretana, RandomToken.sha256(pozivnica));
		String nedim = token("nedim.prihvat@example.com", "Nedim");

		prihvati(nedim, pozivnica).andExpect(status().isGone());
	}

	@Test
	void nepoznatTokenVraca404() throws Exception {
		prihvati(admin, RandomToken.generate()).andExpect(status().isNotFound());
	}

	@Test
	void bezPrijaveSePozivnicaNeMozePrihvatiti() throws Exception {
		mockMvc.perform(post("/api/invites/accept")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"token": "%s"}
						""".formatted(pozovi("selver.prihvat@example.com", "CLIENT"))))
				.andExpect(status().isUnauthorized());
	}

}
