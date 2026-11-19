package ba.slotbook.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.jayway.jsonpath.JsonPath;

import ba.slotbook.TestcontainersConfiguration;

// Token se čita iz tijela obavijesti koja čeka slanje, jer je to jedino mjesto gdje postoji
// u čitljivom obliku. Baza čuva samo hash.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class EmailVerificationApiTest {

	private static final Pattern LINK = Pattern.compile("http://localhost:5173/potvrda-emaila#token=([A-Za-z0-9_-]+)");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	private String email;
	private UUID korisnik;

	@BeforeEach
	void registracija() throws Exception {
		email = "selma." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
		String odgovor = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "%s", "password": "jajce-vodopad-1461", "displayName": "Selma"}
						""".formatted(email)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.emailVerified").value(false))
				.andReturn().getResponse().getContentAsString();
		korisnik = UUID.fromString(JsonPath.read(odgovor, "$.id"));
	}

	private String tokenIzMaila() {
		String tijelo = jdbc.queryForObject("select body from notifications where user_id = ? and "
				+ "type = 'EMAIL_VERIFICATION' and status = 'PENDING' order by created_at desc limit 1", String.class,
				korisnik);
		Matcher link = LINK.matcher(tijelo);
		assertThat(link.find()).as("link u mailu: %s", tijelo).isTrue();
		return link.group(1);
	}

	private ResultActions potvrdi(String token) throws Exception {
		return mockMvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"token": "%s"}
						""".formatted(token)));
	}

	private String prijava() throws Exception {
		return JsonPath.read(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "%s", "password": "jajce-vodopad-1461"}
						""".formatted(email)))
				.andReturn().getResponse().getContentAsString(), "$.accessToken");
	}

	@Test
	void linkIzMailaPotvrdjujeEmailBezPrijave() throws Exception {
		potvrdi(tokenIzMaila()).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + prijava()))
				.andExpect(jsonPath("$.emailVerified").value(true));
	}

	@Test
	void linkSeMozeIskoristitiSamoJednom() throws Exception {
		String token = tokenIzMaila();
		potvrdi(token).andExpect(status().isNoContent());

		potvrdi(token).andExpect(status().isConflict());
	}

	@Test
	void istekaoLinkVraca410IEmailOstajeNepotvrdjen() throws Exception {
		String token = tokenIzMaila();
		jdbc.update("update user_tokens set created_at = now() - interval '2 days', expires_at = now() - interval '1 second' "
				+ "where user_id = ?", korisnik);

		potvrdi(token).andExpect(status().isGone())
				.andExpect(jsonPath("$.detail").value("Link za potvrdu emaila je istekao. Zatraži novi link na stranici za prijavu."));
		assertThat(jdbc.queryForObject("select email_verified_at from users where id = ?", Object.class, korisnik))
				.isNull();
	}

	@Test
	void izmisljenTokenVraca404() throws Exception {
		potvrdi("ovo-nije-pravi-token").andExpect(status().isNotFound());
	}

	// Baza ne smije imati token u čitljivom obliku, samo hash
	@Test
	void bazaCuvaSamoHashTokena() {
		String token = tokenIzMaila();

		assertThat(jdbc.queryForObject("select token_hash from user_tokens where user_id = ?", String.class, korisnik))
				.isEqualTo(RandomToken.sha256(token)).isNotEqualTo(token);
	}

	private ResultActions prijava(String lozinka) throws Exception {
		return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "%s"}
				""".formatted(email, lozinka)));
	}

	// Inače bi registracija tuđim emailom pa prijava svojom lozinkom otkrila ko već ima nalog: za novi
	// nalog prijava prođe, a za postojeći dobije 401
	@Test
	void nepotvrdjenEmailSeNeMozePrijavitiIOdgovorJeIstiKaoZaPogresnuLozinku() throws Exception {
		String pogresna = prijava("pogresna-lozinka").andExpect(status().isUnauthorized())
				.andReturn().getResponse().getContentAsString();

		prijava("jajce-vodopad-1461").andExpect(status().isUnauthorized())
				.andExpect(content().json(pogresna))
				.andExpect(jsonPath("$.accessToken").doesNotExist());
	}

	private ResultActions noviLink(String email) throws Exception {
		return mockMvc.perform(post("/api/auth/email-verification").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "%s"}
						""".formatted(email)));
	}

	private long mailova() {
		return jdbc.queryForObject("select count(*) from notifications where user_id = ? and type = "
				+ "'EMAIL_VERIFICATION'", Long.class, korisnik);
	}

	// Bez prijave, jer se nepotvrđen nalog ne može prijaviti. Isti 204 i za nepoznat i za potvrđen
	// email, da se po odgovoru ne vidi ništa o nalogu.
	@Test
	void noviLinkStizeSamoNepotvrdjenomNalogu() throws Exception {
		noviLink(email.toUpperCase()).andExpect(status().isNoContent());
		noviLink("niko.nepoznat@example.com").andExpect(status().isNoContent());
		assertThat(mailova()).isEqualTo(2);

		potvrdi(tokenIzMaila()).andExpect(status().isNoContent());
		noviLink(email).andExpect(status().isNoContent());
		assertThat(mailova()).isEqualTo(2);
	}

	// Prvi mail je od registracije. Preko granice je odgovor isti 204, samo mail ne nastane: 429 bi
	// dobio samo email koji ima nalog.
	@Test
	void cetvrtiLinkUSatuNeNastajeAOdgovorJeIsti() throws Exception {
		for (int i = 0; i < 3; i++) {
			noviLink(email).andExpect(status().isNoContent());
		}

		assertThat(mailova()).isEqualTo(3);
	}

	// Čeka se dok najstariji od tri maila ne izađe iz sata. Mail od registracije je star 61 minutu, a
	// druga dva su nova: mjesto za jedan novi je slobodno.
	@Test
	void mjestoSeOslobadjaKadNajstarijiMailIzadjeIzSata() throws Exception {
		jdbc.update("update notifications set created_at = created_at - interval '61 minutes' where user_id = ?",
				korisnik);
		noviLink(email).andExpect(status().isNoContent());
		noviLink(email).andExpect(status().isNoContent());

		noviLink(email).andExpect(status().isNoContent());
		assertThat(mailova()).isEqualTo(4);

		noviLink(email).andExpect(status().isNoContent());
		assertThat(mailova()).isEqualTo(4);
	}

	@Test
	void neispravanEmailZaNoviLinkVraca400() throws Exception {
		noviLink("nije-email").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.greske[0].polje").value("email"));
	}

}
