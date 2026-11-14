package ba.slotbook.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
class OrganizationInvitesApiTest {

	private static final String LOZINKA = "igman1984zimska";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository users;

	@Autowired
	OrganizationRepository organizations;

	@Autowired
	OrganizationMemberRepository members;

	@Autowired
	JdbcTemplate jdbc;

	private String dino;
	private String ajla;
	private String kenan;
	private UUID konsultacije;

	// Baza se dijeli između testova bez rollbacka, pa se sve pravi samo ako već ne postoji
	@BeforeEach
	void dinoJeAdminAjlaKlijentKenanAdminDrugeOrganizacije() throws Exception {
		dino = token("dino.poziv@example.com", "Dino");
		ajla = token("ajla.poziv@example.com", "Ajla");
		kenan = token("kenan.poziv@example.com", "Kenan");

		konsultacije = organizacija(dino, "konsultacije-pmf");
		organizacija(kenan, "sala-kenan");

		User ajlaKorisnik = users.findByEmail("ajla.poziv@example.com").orElseThrow();
		if (!members.existsById(new OrganizationMember.Id(konsultacije, ajlaKorisnik.getId()))) {
			members.save(new OrganizationMember(organizations.findById(konsultacije).orElseThrow(), ajlaKorisnik,
					OrganizationRole.CLIENT, Instant.parse("2026-11-12T10:00:00Z")));
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

	private ResultActions pozovi(String token, String email, String uloga) throws Exception {
		return mockMvc.perform(post("/api/organizations/{id}/invites", konsultacije)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "%s", "role": "%s"}
						""".formatted(email, uloga)));
	}

	// Token postoji samo u mailu koji čeka slanje, admin ga ne dobija
	private String tokenIzMaila(String email) {
		String tijelo = jdbc.queryForObject("select body from notifications where email = ? and type = "
				+ "'ORGANIZATION_INVITE' and status = 'PENDING' order by created_at desc limit 1", String.class, email);
		Matcher link = Pattern.compile("http://localhost:5173/pozivnica#token=([A-Za-z0-9_-]+)").matcher(tijelo);
		assertThat(link.find()).as("link u mailu: %s", tijelo).isTrue();
		return link.group(1);
	}

	@Test
	void pozivnicaIdeMailomAAdminNeVidiToken() throws Exception {
		pozovi(dino, "Hana.Poziv@Example.com", "CLIENT")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value("hana.poziv@example.com"))
				.andExpect(jsonPath("$.role").value("CLIENT"))
				.andExpect(jsonPath("$.token").doesNotExist());

		assertThat(jdbc.queryForMap("select user_id, subject, body from notifications where email = ?",
				"hana.poziv@example.com"))
				.containsEntry("user_id", null)
				.containsEntry("subject", "Pozivnica na SlotBook: Organizacija konsultacije-pmf")
				.hasEntrySatisfying("body", tijelo -> assertThat((String) tijelo).contains("s ulogom: klijent"));
	}

	@Test
	void bazaCuvaSamoHashTokenaIzMaila() throws Exception {
		pozovi(dino, "selma.poziv@example.com", "CLIENT").andExpect(status().isCreated());
		String token = tokenIzMaila("selma.poziv@example.com");

		assertThat(jdbc.queryForObject("select count(*) from organization_invites where token_hash = ?", Long.class,
				RandomToken.sha256(token))).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from organization_invites where token_hash = ?", Long.class,
				token)).isZero();
	}

	@Test
	void novaPozivnicaZamjenjujeStaruIStariTokenNestaje() throws Exception {
		pozovi(dino, "lamija.poziv@example.com", "CLIENT").andExpect(status().isCreated());
		String stari = tokenIzMaila("lamija.poziv@example.com");
		pozovi(dino, "lamija.poziv@example.com", "PROVIDER").andExpect(status().isCreated());

		assertThat(jdbc.queryForList("select role from organization_invites where email = ? and accepted_at is null",
				String.class, "lamija.poziv@example.com")).containsExactly("PROVIDER");
		assertThat(jdbc.queryForObject("select count(*) from organization_invites where token_hash = ?", Long.class,
				RandomToken.sha256(stari))).isZero();
	}

	@Test
	void clanSeNeMozePonovoPozvati() throws Exception {
		pozovi(dino, "ajla.poziv@example.com", "PROVIDER")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Već je član"));
	}

	@Test
	void klijentNeMozePozivati() throws Exception {
		pozovi(ajla, "emina.poziv@example.com", "ADMIN").andExpect(status().isForbidden());
	}

	@Test
	void adminTudjeOrganizacijeNeMozePozivati() throws Exception {
		pozovi(kenan, "emina.poziv@example.com", "ADMIN").andExpect(status().isForbidden());
	}

	@Test
	void neispravanEmailVraca400ZaTajPolje() throws Exception {
		pozovi(dino, "nije-email", "CLIENT")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.greske[0].polje").value("email"));
	}

}
