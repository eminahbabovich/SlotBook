package ba.slotbook.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.user.UserRepository;

// Male granice samo za ovaj test (vlastiti Spring kontekst), a zaglavlje adrese kao na Renderu.
// Ostali testovi imaju visoke granice (application-default.properties u testovima), jer svi šalju s 127.0.0.1.
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
		"slotbook.rate-limit.login-per-minute=3",
		"slotbook.rate-limit.register-per-hour=2",
		"slotbook.rate-limit.client-ip-header=CF-Connecting-IP" })
@AutoConfigureMockMvc
class AuthRateLimitApiTest {

	private static final String EMAIL = "selma.granica@example.com";
	private static final String LOZINKA = "baščaršija-1462";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository users;

	@BeforeEach
	void selmaImaNalog() throws Exception {
		if (users.findByEmail(EMAIL).isEmpty()) {
			mockMvc.perform(registracija(EMAIL).header("CF-Connecting-IP", "198.51.100.1"))
					.andExpect(status().isCreated());
		}
	}

	private static MockHttpServletRequestBuilder prijava(String lozinka) {
		return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "%s"}
				""".formatted(EMAIL, lozinka));
	}

	private static MockHttpServletRequestBuilder registracija(String email) {
		return post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "%s", "displayName": "Selma"}
				""".formatted(email, LOZINKA));
	}

	@Test
	void cetvrtaPrijavaSIsteAdreseDobija429ICekanjeIIspravnaLozinkaNePomaze() throws Exception {
		for (int i = 0; i < 3; i++) {
			mockMvc.perform(prijava("pogresna-lozinka").header("CF-Connecting-IP", "203.0.113.7"))
					.andExpect(status().isUnauthorized());
		}

		String cekanje = mockMvc.perform(prijava(LOZINKA).header("CF-Connecting-IP", "203.0.113.7"))
				.andExpect(status().isTooManyRequests())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(429))
				.andExpect(jsonPath("$.title").value("Previše pokušaja"))
				.andReturn().getResponse().getHeader("Retry-After");

		// 3 pokušaja u minuti se vraćaju po jedan svakih 20 s
		assertThat(Integer.parseInt(cekanje)).isBetween(1, 20);
		mockMvc.perform(prijava(LOZINKA).header("CF-Connecting-IP", "203.0.113.8"))
				.andExpect(status().isOk());
	}

	// Cloudflare prepiše CF-Connecting-IP, a X-Forwarded-For samo dopuni, pa njemu pozivalac bira lijevi dio
	@Test
	void lazanXForwardedForNeDajeNovePokusaje() throws Exception {
		for (int i = 0; i < 3; i++) {
			mockMvc.perform(prijava("pogresna-lozinka").header("CF-Connecting-IP", "203.0.113.20")
					.header("X-Forwarded-For", "10.0.0." + i))
					.andExpect(status().isUnauthorized());
		}

		mockMvc.perform(prijava(LOZINKA).header("CF-Connecting-IP", "203.0.113.20")
				.header("X-Forwarded-For", "10.0.0.99"))
				.andExpect(status().isTooManyRequests());
	}

	// Bez zaglavlja (npr. zahtjev koji ne ide kroz Cloudflare) broji se adresa konekcije
	@Test
	void bezZaglavljaSeBrojiAdresaKonekcije() throws Exception {
		for (int i = 0; i < 3; i++) {
			mockMvc.perform(prijava("pogresna-lozinka").with(r -> {
				r.setRemoteAddr("192.0.2.1");
				return r;
			})).andExpect(status().isUnauthorized());
		}

		mockMvc.perform(prijava(LOZINKA).with(r -> {
			r.setRemoteAddr("192.0.2.1");
			return r;
		})).andExpect(status().isTooManyRequests());
		mockMvc.perform(prijava(LOZINKA).with(r -> {
			r.setRemoteAddr("192.0.2.2");
			return r;
		})).andExpect(status().isOk());
	}

	@Test
	void trecaRegistracijaSIsteAdreseDobija429INalogNeNastaje() throws Exception {
		mockMvc.perform(registracija("amar.granica1@example.com").header("CF-Connecting-IP", "203.0.113.30"))
				.andExpect(status().isCreated());
		mockMvc.perform(registracija("amar.granica2@example.com").header("CF-Connecting-IP", "203.0.113.30"))
				.andExpect(status().isCreated());

		mockMvc.perform(registracija("amar.granica3@example.com").header("CF-Connecting-IP", "203.0.113.30"))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.title").value("Previše pokušaja"));

		assertThat(users.findByEmail("amar.granica3@example.com")).isEmpty();
	}

	// Prijave i registracije imaju svaka svoju granicu: potrošene prijave ne blokiraju registraciju
	@Test
	void potrosenePrijaveNeBlokirajuRegistraciju() throws Exception {
		for (int i = 0; i < 4; i++) {
			mockMvc.perform(prijava("pogresna-lozinka").header("CF-Connecting-IP", "203.0.113.40"));
		}

		mockMvc.perform(registracija("amar.granica4@example.com").header("CF-Connecting-IP", "203.0.113.40"))
				.andExpect(status().isCreated());
	}

}
