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
				.andExpect(jsonPath("$.detail").value("Link za potvrdu emaila je istekao. Zatraži novi link u profilu."));
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

	@Test
	void noviLinkStizeSamoDokEmailNijePotvrdjen() throws Exception {
		String pristup = prijava();
		mockMvc.perform(post("/api/me/email-verification").header("Authorization", "Bearer " + pristup))
				.andExpect(status().isNoContent());
		potvrdi(tokenIzMaila()).andExpect(status().isNoContent());
		mockMvc.perform(post("/api/me/email-verification").header("Authorization", "Bearer " + pristup))
				.andExpect(status().isNoContent());

		assertThat(jdbc.queryForObject("select count(*) from notifications where user_id = ? and type = "
				+ "'EMAIL_VERIFICATION'", Long.class, korisnik)).isEqualTo(2);
	}

	@Test
	void noviLinkTraziPrijavu() throws Exception {
		mockMvc.perform(post("/api/me/email-verification")).andExpect(status().isUnauthorized());
	}

	private ResultActions noviLink(String pristup) throws Exception {
		return mockMvc.perform(post("/api/me/email-verification").header("Authorization", "Bearer " + pristup));
	}

	private long mailova() {
		return jdbc.queryForObject("select count(*) from notifications where user_id = ? and type = "
				+ "'EMAIL_VERIFICATION'", Long.class, korisnik);
	}

	// Prvi mail je od registracije, pa ostaju još dva nova linka u satu
	@Test
	void cetvrtiLinkUSatuDobija429IMailNeNastaje() throws Exception {
		String pristup = prijava();
		noviLink(pristup).andExpect(status().isNoContent());
		noviLink(pristup).andExpect(status().isNoContent());

		String cekanje = noviLink(pristup)
				.andExpect(status().isTooManyRequests())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Previše mailova"))
				.andReturn().getResponse().getHeader("Retry-After");

		assertThat(Long.parseLong(cekanje)).isBetween(3500L, 3600L);
		assertThat(mailova()).isEqualTo(3);
	}

	// Čeka se dok najstariji od tri maila ne izađe iz sata. Mail od registracije je star 59 minuta, a
	// druga dva su nova: čeka se još minuta, a ne sat od najnovijeg.
	@Test
	void cekanjeTrajeDokNajstarijiMailNeIzadjeIzSata() throws Exception {
		jdbc.update("update notifications set created_at = created_at - interval '59 minutes' where user_id = ?",
				korisnik);
		String pristup = prijava();
		noviLink(pristup).andExpect(status().isNoContent());
		noviLink(pristup).andExpect(status().isNoContent());

		String cekanje = noviLink(pristup).andExpect(status().isTooManyRequests())
				.andReturn().getResponse().getHeader("Retry-After");
		assertThat(Long.parseLong(cekanje)).isBetween(1L, 60L);

		jdbc.update("update notifications set created_at = created_at - interval '2 minutes' where user_id = ?",
				korisnik);
		noviLink(pristup).andExpect(status().isNoContent());
		assertThat(mailova()).isEqualTo(4);
	}
}
