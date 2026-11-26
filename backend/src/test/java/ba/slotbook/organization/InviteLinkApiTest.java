package ba.slotbook.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import ba.slotbook.auth.LinkToken;
import ba.slotbook.user.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class InviteLinkApiTest {

	private static final String LOZINKA = "plakat-u-citaonici-7";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository users;

	@Autowired
	OrganizationRepository organizations;

	@Autowired
	JdbcTemplate jdbc;

	private String lejla;
	private String tarik;
	private UUID citaonica;
	private UUID teretana;

	// Lejla je admin čitaonice, Tarik admin teretane. Svaki test pravi svoj link.
	@BeforeEach
	void lejlaAdminCitaoniceTarikAdminTeretane() throws Exception {
		lejla = token("lejla.link@example.com", "Lejla Begić", true);
		tarik = token("tarik.link@example.com", "Tarik Đulić", true);
		citaonica = organizacija(lejla, "citaonica-link");
		teretana = organizacija(tarik, "teretana-link");
	}

	@Test
	void adminPraviLinkABazaCuvaSamoHashTokena() throws Exception {
		String tijelo = napravi(citaonica, lejla, 30, 7)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.uses").value(0))
				.andExpect(jsonPath("$.maxUses").value(30))
				.andExpect(jsonPath("$.active").value(true))
				.andReturn().getResponse().getContentAsString();
		String url = JsonPath.read(tijelo, "$.url");
		String token = url.substring(url.lastIndexOf('/') + 1);

		assertThat(url).contains("/pridruzi/");
		assertThat(jdbc.queryForObject("select token_hash from invite_links where id = ?::uuid", String.class,
				(Object) JsonPath.read(tijelo, "$.id"))).isEqualTo(LinkToken.hash(token)).isNotEqualTo(token);
		mockMvc.perform(get("/api/organizations/{id}/invite-links", citaonica).header("Authorization", "Bearer " + lejla))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].url").value(url));
		assertThat(radnje()).contains("INVITE_LINK_CREATED");
	}

	// Ista adresa i sedmicu kasnije: token se računa iz ID-ja, ne čuva se
	@Test
	void listaVracaIstuAdresuKaoPravljenje() throws Exception {
		String url = JsonPath.read(napravi(citaonica, lejla, 5, 7).andReturn().getResponse().getContentAsString(), "$.url");

		String lista = mockMvc.perform(get("/api/organizations/{id}/invite-links", citaonica)
				.header("Authorization", "Bearer " + lejla)).andReturn().getResponse().getContentAsString();

		assertThat(JsonPath.<List<String>>read(lista, "$.content[*].url")).contains(url);
	}

	@Test
	void pregledBezPrijavePokazeImeOrganizacijeANepostojeciVraca404() throws Exception {
		String token = token(napravi(citaonica, lejla, 5, 7));

		mockMvc.perform(get("/api/invite-links/{token}", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.organizationName").value("Organizacija citaonica-link"))
				.andExpect(jsonPath("$.usable").value(true))
				.andExpect(jsonPath("$.uses").doesNotExist());
		mockMvc.perform(get("/api/invite-links/{token}", "nema-ovakvog-tokena")).andExpect(status().isNotFound());
	}

	@Test
	void ulazakPrekoLinkaDajeUloguKlijentaIBrojiKoristenje() throws Exception {
		String token = token(napravi(citaonica, lejla, 5, 7));
		String amra = token("amra.link@example.com", "Amra Čaušević", true);

		pridruzi(amra, token)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.alreadyMember").value(false))
				.andExpect(jsonPath("$.slug").value("citaonica-link"));

		assertThat(uloga("amra.link@example.com")).isEqualTo("CLIENT");
		assertThat(koristenja()).isEqualTo(1);
		assertThat(radnje()).contains("INVITE_LINK_JOINED");
	}

	// Lejla je već admin: poruka "već si član", mjesto se ne troši, a uloga ostaje admin
	@Test
	void vecClanDobijePorukuINeTrosiMjestoNiUlogu() throws Exception {
		String token = token(napravi(citaonica, lejla, 5, 7));

		pridruzi(lejla, token).andExpect(status().isOk()).andExpect(jsonPath("$.alreadyMember").value(true));

		assertThat(koristenja()).isZero();
		assertThat(uloga("lejla.link@example.com")).isEqualTo("ADMIN");
	}

	// Prijava već traži potvrđen email, pa ovo je druga linija: token izdat prije nego što je potvrda
	// skinuta (npr. promjena emaila kasnije) ne smije biti dovoljan za ulazak
	@Test
	void nepotvrdjenEmailDobija403INijeClan() throws Exception {
		String token = token(napravi(citaonica, lejla, 5, 7));
		String nepotvrdjen = token("dino.nepotvrden@example.com", "Dino", true);
		jdbc.update("update users set email_verified_at = null where email = 'dino.nepotvrden@example.com'");

		pridruzi(nepotvrdjen, token)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.razlog").value("EMAIL_NOT_VERIFIED"));

		assertThat(uloga("dino.nepotvrden@example.com")).isNull();
		assertThat(koristenja()).isZero();
	}

	@Test
	void povucenLinkVraca410AOstajeUListi() throws Exception {
		String tijelo = napravi(citaonica, lejla, 5, 7).andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(tijelo, "$.id");

		for (int i = 0; i < 2; i++) {
			mockMvc.perform(delete("/api/organizations/{id}/invite-links/{l}", citaonica, id)
					.header("Authorization", "Bearer " + lejla)).andExpect(status().isNoContent());
		}

		pridruzi(token("emina.link@example.com", "Emina", true), izTijela(tijelo))
				.andExpect(status().isGone())
				.andExpect(jsonPath("$.razlog").value("REVOKED"));
		// Drugo povlačenje ne piše novi zapis
		assertThat(radnje().stream().filter("INVITE_LINK_REVOKED"::equals)).hasSize(1);
	}

	@Test
	void istekaoLinkVraca410() throws Exception {
		String tijelo = napravi(citaonica, lejla, 5, 7).andReturn().getResponse().getContentAsString();
		jdbc.update("update invite_links set created_at = now() - interval '9 days', expires_at = now() - interval "
				+ "'2 days' where id = ?::uuid", (Object) JsonPath.read(tijelo, "$.id"));

		pridruzi(token("emina.link@example.com", "Emina", true), izTijela(tijelo))
				.andExpect(status().isGone())
				.andExpect(jsonPath("$.razlog").value("EXPIRED"));
	}

	// Tarik je admin, ali teretane: @PreAuthorize za njegovu organizaciju prođe, a link čitaonice se
	// ne nađe, jer servis traži link unutar organizacije iz putanje
	@Test
	void adminDrugeOrganizacijeNePovlaciTudjiLinkKrozSvojuOrganizaciju() throws Exception {
		String tijelo = napravi(citaonica, lejla, 5, 7).andReturn().getResponse().getContentAsString();

		mockMvc.perform(delete("/api/organizations/{id}/invite-links/{l}", teretana, JsonPath.read(tijelo, "$.id"))
				.header("Authorization", "Bearer " + tarik)).andExpect(status().isNotFound());

		assertThat(jdbc.queryForObject("select revoked_at is null from invite_links where id = ?::uuid",
				Boolean.class, (Object) JsonPath.read(tijelo, "$.id"))).isTrue();
	}

	@Test
	void granicaKoristenjaMoraBitiOdJedanDoHiljadu() throws Exception {
		napravi(citaonica, lejla, 0, 7).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.greske[0].polje").value("maxUses"));
		napravi(citaonica, lejla, 5, 91).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.greske[0].polje").value("days"));
	}

	private ResultActions napravi(UUID organizacija, String token, int mjesta, int dana) throws Exception {
		return mockMvc.perform(post("/api/organizations/{id}/invite-links", organizacija)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"maxUses": %d, "days": %d}
						""".formatted(mjesta, dana)));
	}

	private static String token(ResultActions napravljen) throws Exception {
		return izTijela(napravljen.andReturn().getResponse().getContentAsString());
	}

	private static String izTijela(String tijelo) {
		String url = JsonPath.read(tijelo, "$.url");
		return url.substring(url.lastIndexOf('/') + 1);
	}

	private ResultActions pridruzi(String token, String linkToken) throws Exception {
		return mockMvc.perform(post("/api/invite-links/join")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"token": "%s"}
						""".formatted(linkToken)));
	}

	private int koristenja() {
		return jdbc.queryForObject("select coalesce(sum(uses), 0) from invite_links where organization_id = ?",
				Integer.class, citaonica);
	}

	private String uloga(String email) {
		return jdbc.query("select m.role from organization_members m join users u on u.id = m.user_id "
				+ "where m.organization_id = ? and u.email = ?", rs -> rs.next() ? rs.getString(1) : null, citaonica,
				email);
	}

	private List<String> radnje() {
		return jdbc.queryForList("select action from audit_log where organization_id = ? order by id", String.class,
				citaonica);
	}

	private String token(String email, String ime, boolean potvrden) throws Exception {
		if (users.findByEmail(email).isEmpty()) {
			mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
					{"email": "%s", "password": "%s", "displayName": "%s"}
					""".formatted(email, LOZINKA, ime))).andExpect(status().isAccepted());
		}
		if (potvrden) {
			jdbc.update("update users set email_verified_at = now() where email = lower(?)", email);
		}
		String tijelo = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "%s"}
				""".formatted(email, LOZINKA))).andReturn().getResponse().getContentAsString();
		return JsonPath.read(tijelo, "$.accessToken");
	}

	// Svaka organizacija je nova u svakom testu, da linkovi i dnevnik iz drugih testova ne smetaju
	private UUID organizacija(String token, String slug) throws Exception {
		organizations.findBySlug(slug).ifPresent(o -> {
			jdbc.update("delete from invite_links where organization_id = ?", o.getId());
			jdbc.update("delete from organization_members where organization_id = ? and role = 'CLIENT'", o.getId());
			jdbc.update("delete from audit_log where organization_id = ?", o.getId());
		});
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

}
