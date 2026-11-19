package ba.slotbook.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ba.slotbook.TestcontainersConfiguration;

// Mjeri vrijeme odgovora kroz MockMvc, dakle samo rad servera i baze, bez mreže. Granica od 50 ms hvata
// grubu razliku (preskočen BCrypt, sinhrono slanje maila), a nije dokaz da razlike nema: za reset lozinke
// postojeći email radi više upisa i sporiji je za desetak milisekundi (docs/ODLUKE.md).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class VrijemeOdgovoraTest {

	private static final int ZAHTJEVA = 30;
	// BCrypt ovdje traje oko 200 ms, pa bi grana koja ga preskoči bila brža bar toliko
	private static final long NAJVECA_RAZLIKA_MS = 50;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	private static String noviEmail(String ime) {
		return ime + "." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
	}

	private static MockHttpServletRequestBuilder registracija(String email) {
		return post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "kozija-cuprija-1", "displayName": "Amar"}
				""".formatted(email));
	}

	private static MockHttpServletRequestBuilder reset(String email) {
		return post("/api/auth/password-reset").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s"}
				""".formatted(email));
	}

	private long ms(MockHttpServletRequestBuilder zahtjev) throws Exception {
		long pocetak = System.nanoTime();
		mockMvc.perform(zahtjev).andExpect(status().is2xxSuccessful());
		return (System.nanoTime() - pocetak) / 1_000_000;
	}

	private static long medijana(List<Long> vremena) {
		List<Long> sortirana = vremena.stream().sorted().toList();
		return sortirana.get(sortirana.size() / 2);
	}

	// Postojeći nalozi su potvrđeni, da im ide mail "nalog postoji", a ne novi link. Svaki je drugi,
	// da granica od tri maila na sat ne preskoči slanje.
	private List<String> potvrdjeniNalozi() throws Exception {
		List<String> emailovi = new ArrayList<>();
		for (int i = 0; i < ZAHTJEVA; i++) {
			String email = noviEmail("postoji");
			mockMvc.perform(registracija(email)).andExpect(status().isAccepted());
			jdbc.update("update users set email_verified_at = now() where email = ?", email);
			emailovi.add(email);
		}
		return emailovi;
	}

	@Test
	void registracijaZaPostojeciEmailTrosiBcryptKaoZaNovi() throws Exception {
		List<String> postoje = potvrdjeniNalozi();
		List<Long> novi = new ArrayList<>();
		List<Long> postojeci = new ArrayList<>();
		// Naizmjenično, da zagrijavanje i šum pogode obje grupe jednako
		for (int i = 0; i < ZAHTJEVA; i++) {
			novi.add(ms(registracija(noviEmail("novi"))));
			postojeci.add(ms(registracija(postoje.get(i))));
		}

		System.out.printf("Registracija, medijana od %d: novi email %d ms, postojeći %d ms%n", ZAHTJEVA,
				medijana(novi), medijana(postojeci));
		assertThat(Math.abs(medijana(novi) - medijana(postojeci))).isLessThan(NAJVECA_RAZLIKA_MS);
	}

	@Test
	void resetLozinkeZaPostojeciEmailNijeSporijiZaViseOd50Ms() throws Exception {
		List<String> postoje = potvrdjeniNalozi();
		List<Long> nepoznati = new ArrayList<>();
		List<Long> postojeci = new ArrayList<>();
		for (int i = 0; i < ZAHTJEVA; i++) {
			nepoznati.add(ms(reset(noviEmail("niko"))));
			postojeci.add(ms(reset(postoje.get(i))));
		}

		System.out.printf("Reset lozinke, medijana od %d: nepoznat email %d ms, postojeći %d ms%n", ZAHTJEVA,
				medijana(nepoznati), medijana(postojeci));
		assertThat(Math.abs(medijana(nepoznati) - medijana(postojeci))).isLessThan(NAJVECA_RAZLIKA_MS);
	}

}
