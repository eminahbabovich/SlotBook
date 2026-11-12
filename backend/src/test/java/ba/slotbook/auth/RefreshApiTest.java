package ba.slotbook.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.user.UserRepository;
import jakarta.servlet.http.Cookie;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class RefreshApiTest {

	private static final String EMAIL = "aida.refresh@example.com";
	private static final String LOZINKA = "kovaci1992";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void korisnikPostoji() throws Exception {
		if (users.findByEmail(EMAIL).isEmpty()) {
			mockMvc.perform(post("/api/auth/register")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"email": "%s", "password": "%s", "displayName": "Aida"}
							""".formatted(EMAIL, LOZINKA)))
					.andExpect(status().isCreated());
		}
	}

	private MvcResult prijava() throws Exception {
		return mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email": "%s", "password": "%s"}
								""".formatted(EMAIL, LOZINKA)))
				.andExpect(status().isOk())
				.andReturn();
	}

	private static String refreshIz(MvcResult odgovor) {
		return odgovor.getResponse().getCookie(AuthController.REFRESH_KOLACIC).getValue();
	}

	private ResultActions refresh(String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/auth/refresh")
				.cookie(new Cookie(AuthController.REFRESH_KOLACIC, refreshToken)));
	}

	@Test
	void prijavaPostavljaKolacicKojiJavaScriptNeMozeProcitati() throws Exception {
		String setCookie = prijava().getResponse().getHeader(HttpHeaders.SET_COOKIE);

		assertThat(setCookie).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth");
	}

	@Test
	void refreshVracaNoviAccessTokenINoviRefreshToken() throws Exception {
		String prvi = refreshIz(prijava());

		MvcResult odgovor = refresh(prvi)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn();

		assertThat(refreshIz(odgovor)).isNotEqualTo(prvi);
	}

	@Test
	void ponovoPoslanStariTokenPonistavaCijeluPorodicu() throws Exception {
		String prvi = refreshIz(prijava());
		String drugi = refreshIz(refresh(prvi).andExpect(status().isOk()).andReturn());

		// Neko šalje token koji je već zamijenjen: ili lopov ili pravi korisnik, ne znamo koji
		refresh(prvi).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.title").value("Sesija je istekla"));

		// Zato ni najnoviji token iz te porodice više ne važi
		refresh(drugi).andExpect(status().isUnauthorized());
	}

	@Test
	void ponistenaPorodicaNeDiraDrugePrijave() throws Exception {
		String telefon = refreshIz(prijava());
		String laptop = refreshIz(prijava());

		refresh(telefon).andExpect(status().isOk());
		refresh(telefon).andExpect(status().isUnauthorized());

		refresh(laptop).andExpect(status().isOk());
	}

	@Test
	void refreshBezKolacicaVraca401() throws Exception {
		mockMvc.perform(post("/api/auth/refresh"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void istekaoRefreshTokenSeOdbija() throws Exception {
		String token = refreshIz(prijava());
		jdbc.update("update refresh_tokens set expires_at = now() - interval '1 minute' where token_hash = ?",
				RefreshTokenService.hash(token));

		refresh(token).andExpect(status().isUnauthorized());
	}

	@Test
	void odjavaBriseKolacicIPonistavaToken() throws Exception {
		String token = refreshIz(prijava());

		mockMvc.perform(post("/api/auth/logout").cookie(new Cookie(AuthController.REFRESH_KOLACIC, token)))
				.andExpect(status().isNoContent())
				.andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
						containsString("refresh_token=;"), containsString("Max-Age=0"))));

		refresh(token).andExpect(status().isUnauthorized());
	}

	@Test
	void uBaziJeSamoHashTokena() throws Exception {
		String token = refreshIz(prijava());

		Integer saTokenom = jdbc.queryForObject(
				"select count(*) from refresh_tokens where token_hash = ?", Integer.class, token);
		Integer saHashom = jdbc.queryForObject(
				"select count(*) from refresh_tokens where token_hash = ?", Integer.class,
				RefreshTokenService.hash(token));

		assertThat(saTokenom).isZero();
		assertThat(saHashom).isEqualTo(1);
	}

}
