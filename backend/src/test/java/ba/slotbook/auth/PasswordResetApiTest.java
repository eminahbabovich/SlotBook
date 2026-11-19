package ba.slotbook.auth;

import static org.assertj.core.api.Assertions.assertThat;
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
import jakarta.servlet.http.Cookie;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetApiTest {

	private static final String STARA = "jajce-vodopad-1461";
	private static final String NOVA = "ćevapi-na-baščaršiji-7";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	private String email;
	private UUID korisnik;

	@BeforeEach
	void registracija() throws Exception {
		email = "dzenana." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
		String odgovor = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "%s", "password": "%s", "displayName": "Dženana"}
						""".formatted(email, STARA)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		korisnik = UUID.fromString(JsonPath.read(odgovor, "$.id"));
	}

	private void trazi(String email) throws Exception {
		mockMvc.perform(post("/api/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "%s"}
						""".formatted(email)))
				.andExpect(status().isNoContent());
	}

	private String tokenIzMaila(String tip, String putanja) {
		String tijelo = jdbc.queryForObject("select body from notifications where user_id = ? and type = ? "
				+ "and status = 'PENDING' order by created_at desc limit 1", String.class, korisnik, tip);
		Matcher link = Pattern.compile("http://localhost:5173/" + putanja + "#token=([A-Za-z0-9_-]+)").matcher(tijelo);
		assertThat(link.find()).as("link u mailu: %s", tijelo).isTrue();
		return link.group(1);
	}

	private String noviLink() throws Exception {
		trazi(email);
		return tokenIzMaila("PASSWORD_RESET", "nova-lozinka");
	}

	private ResultActions postavi(String token, String lozinka) throws Exception {
		return mockMvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"token": "%s", "password": "%s"}
						""".formatted(token, lozinka)));
	}

	// Za testove koji se prijavljuju starom lozinkom: nepotvrđen email se ne može prijaviti
	private void potvrdiEmail() {
		jdbc.update("update users set email_verified_at = now() where id = ?", korisnik);
	}

	private ResultActions prijava(String lozinka) throws Exception {
		return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "%s", "password": "%s"}
						""".formatted(email, lozinka)));
	}

	@Test
	void novaLozinkaVrijediAStaraViseNe() throws Exception {
		postavi(noviLink(), NOVA).andExpect(status().isNoContent());

		prijava(STARA).andExpect(status().isUnauthorized());
		prijava(NOVA).andExpect(status().isOk());
	}

	// Kriterij Faze 5: isti link ne radi drugi put, ni s drugom lozinkom
	@Test
	void istiLinkZaNovuLozinkuNeRadiDvaput() throws Exception {
		String token = noviLink();
		postavi(token, NOVA).andExpect(status().isNoContent());

		postavi(token, "lopov-pokusava-ponovo-3").andExpect(status().isConflict());
		prijava(NOVA).andExpect(status().isOk());
	}

	// Link je stigao na taj email, pa je i to potvrda emaila
	@Test
	void novaLozinkaPotvrdjujeEmail() throws Exception {
		postavi(noviLink(), NOVA).andExpect(status().isNoContent());

		assertThat(jdbc.queryForObject("select email_verified_at from users where id = ?", Object.class, korisnik))
				.isNotNull();
	}

	// Isti odgovor kao za postojeći nalog, inače bi se ovim endpointom moglo provjeriti ko ima nalog
	@Test
	void nepoznatEmailDobijaIstiOdgovorIMailNeNastaje() throws Exception {
		Long prije = jdbc.queryForObject("select count(*) from notifications where type = 'PASSWORD_RESET'", Long.class);

		trazi("niko.nema.nalog@example.com");

		assertThat(jdbc.queryForObject("select count(*) from notifications where type = 'PASSWORD_RESET'", Long.class))
				.isEqualTo(prije);
	}

	@Test
	void novaLozinkaOdjavljujeSveUredjaje() throws Exception {
		potvrdiEmail();
		String refresh = prijava(STARA).andReturn().getResponse().getCookie(AuthController.REFRESH_KOLACIC).getValue();

		postavi(noviLink(), NOVA).andExpect(status().isNoContent());

		mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(AuthController.REFRESH_KOLACIC, refresh)))
				.andExpect(status().isUnauthorized());
	}

	// Dva zahtjeva za reset, pa dva linka u inboxu. Kad jedan promijeni lozinku, drugi više ne važi.
	@Test
	void stariLinkNeVaziPoslijePromjeneLozinke() throws Exception {
		String prvi = noviLink();
		String drugi = noviLink();
		postavi(drugi, NOVA).andExpect(status().isNoContent());

		postavi(prvi, "lopov-zna-stari-link-9").andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Link za novu lozinku ne važi"));
		prijava(NOVA).andExpect(status().isOk());
	}

	@Test
	void linkZaPotvrduEmailaNeMozePostavitiLozinku() throws Exception {
		potvrdiEmail();
		postavi(tokenIzMaila("EMAIL_VERIFICATION", "potvrda-emaila"), NOVA).andExpect(status().isNotFound());

		prijava(STARA).andExpect(status().isOk());
	}

	@Test
	void istekaoLinkVraca410ILozinkaOstajeIsta() throws Exception {
		potvrdiEmail();
		String token = noviLink();
		jdbc.update("update user_tokens set created_at = now() - interval '2 hours', expires_at = now() - interval "
				+ "'1 second' where user_id = ? and type = 'PASSWORD_RESET'", korisnik);

		postavi(token, NOVA).andExpect(status().isGone())
				.andExpect(jsonPath("$.detail").value("Link za novu lozinku je istekao. Zatraži novi link na stranici za prijavu."));
		prijava(STARA).andExpect(status().isOk());
	}

	@Test
	void prekratkaNovaLozinkaVraca400ILinkOstajeNeiskoristen() throws Exception {
		String token = noviLink();
		postavi(token, "kratka").andExpect(status().isBadRequest());

		postavi(token, NOVA).andExpect(status().isNoContent());
	}

	@Test
	void linkZaNovuLozinkuVaziSatVremena() throws Exception {
		noviLink();

		assertThat(jdbc.queryForObject("select extract(epoch from expires_at - created_at) from user_tokens "
				+ "where user_id = ? and type = 'PASSWORD_RESET'", Double.class, korisnik)).isEqualTo(3600.0);
	}

	// Najviše 3 maila za reset na sat. Četvrti zahtjev dobije isti odgovor, da se po razlici ne vidi ko ima
	// nalog, a mail ne nastaje. Vlasnik nalog ne gubi: linkovi iz prva tri maila i dalje važe.
	@Test
	void cetvrtiZahtjevUSatuDobijaIstiOdgovorAliNovogMailaNema() throws Exception {
		for (int i = 0; i < 4; i++) {
			trazi(email);
		}

		assertThat(jdbc.queryForObject("select count(*) from notifications where user_id = ? and type = "
				+ "'PASSWORD_RESET'", Long.class, korisnik)).isEqualTo(3);
		assertThat(jdbc.queryForObject("select count(*) from user_tokens where user_id = ? and type = "
				+ "'PASSWORD_RESET'", Long.class, korisnik)).isEqualTo(3);
	}
}
