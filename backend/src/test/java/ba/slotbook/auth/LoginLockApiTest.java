package ba.slotbook.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import ba.slotbook.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class LoginLockApiTest {

	private static final String LOZINKA = "vrelo-bosne-1878";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	LoginLockService zakljucavanje;

	private String email;

	@BeforeEach
	void haris() throws Exception {
		email = "haris." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "%s", "displayName": "Haris"}
				""".formatted(email, LOZINKA))).andExpect(status().isCreated());
	}

	private ResultActions prijava(String email, String lozinka) throws Exception {
		return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "%s"}
				""".formatted(email, lozinka)));
	}

	private void pogresna(String email, int puta) throws Exception {
		for (int i = 0; i < puta; i++) {
			prijava(email, "pogresna-lozinka").andExpect(status().isUnauthorized());
		}
	}

	@Test
	void poslijePetPogresnihIIspravnaLozinkaDobija429BezTokena() throws Exception {
		pogresna(email, 5);

		MvcResult odgovor = prijava(email, LOZINKA)
				.andExpect(status().isTooManyRequests())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Prijava privremeno zaključana"))
				.andExpect(jsonPath("$.accessToken").doesNotExist())
				.andReturn();

		assertThat(odgovor.getResponse().getHeader("Set-Cookie")).isNull();
		assertThat(Long.parseLong(odgovor.getResponse().getHeader("Retry-After"))).isBetween(850L, 900L);
	}

	// Brojanje je po emailu iz zahtjeva, ne po nalogu: nepoznat email se zaključa isto, pa se po
	// odgovoru ne vidi ko ima nalog
	@Test
	void nepoznatEmailSeZakljucavaJednakoKaoPostojeci() throws Exception {
		String nepoznat = "niko." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
		pogresna(email, 5);
		pogresna(nepoznat, 5);

		String postoji = prijava(email, "pogresna-lozinka").andExpect(status().isTooManyRequests())
				.andReturn().getResponse().getContentAsString();
		prijava(nepoznat, "pogresna-lozinka").andExpect(status().isTooManyRequests())
				.andExpect(content().json(postoji));
	}

	@Test
	void uspjesnaPrijavaBriseRanijeGreske() throws Exception {
		pogresna(email, 4);
		prijava(email, LOZINKA).andExpect(status().isOk());

		pogresna(email, 4);
		prijava(email, LOZINKA).andExpect(status().isOk());
	}

	@Test
	void zakljucavanjeDrugogEmailaNeSmeta() throws Exception {
		pogresna(email.toUpperCase(), 5);

		String drugi = "lejla." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "%s", "displayName": "Lejla"}
				""".formatted(drugi, LOZINKA))).andExpect(status().isCreated());

		prijava(email, LOZINKA).andExpect(status().isTooManyRequests());
		prijava(drugi, LOZINKA).andExpect(status().isOk());
	}

	@Test
	void kadZakljucavanjeIstekneIspravnaLozinkaProlazi() throws Exception {
		pogresna(email, 5);
		jdbc.update("update login_failures set locked_until = now() - interval '1 second' where email = ?", email);

		prijava(email, LOZINKA).andExpect(status().isOk());
	}

	// Red za email koji se više ne javlja ne treba čuvati: ni zaključan ni u otvorenom prozoru
	@Test
	void ciscenjeBriseSamoRedoveKojiViseNistaNeZnace() throws Exception {
		String stari = "stari." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
		pogresna(stari, 1);
		pogresna(email, 1);
		jdbc.update("update login_failures set window_start = now() - interval '16 minutes' where email = ?", stari);

		zakljucavanje.ocisti();

		assertThat(jdbc.queryForList("select email from login_failures where email in (?, ?)", String.class, stari,
				email)).containsExactly(email);
	}

}
