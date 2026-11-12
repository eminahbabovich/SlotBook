package ba.slotbook.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.user.User;
import ba.slotbook.user.UserRepository;

// Baza se između testova ne briše, pa svaki test koristi svoj email
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class RegisterApiTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository users;

	private ResultActions registruj(String email, String lozinka, String ime) throws Exception {
		return mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "%s", "password": "%s", "displayName": "%s"}
						""".formatted(email, lozinka, ime)));
	}

	@Test
	void registracijaBezPrijaveVraca201IKorisnikaBezLozinke() throws Exception {
		registruj("Lejla.Begic@Example.com", "mostarskiMost1", "Lejla Begić")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.email").value("lejla.begic@example.com"))
				.andExpect(jsonPath("$.displayName").value("Lejla Begić"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void lozinkaSeUBaziCuvaKaoBcryptHash() throws Exception {
		registruj("haris@example.com", "bascarsija99", "Haris").andExpect(status().isCreated());

		User user = users.findByEmail("haris@example.com").orElseThrow();
		assertThat(user.getPasswordHash()).startsWith("$2a$").doesNotContain("bascarsija99");
	}

	@Test
	void istiEmailDrugiPutVraca409() throws Exception {
		registruj("selma@example.com", "trebevic123", "Selma").andExpect(status().isCreated());

		registruj("SELMA@example.com", "drugaLozinka1", "Selma druga")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Email je već registrovan"));
	}

	@Test
	void predugaLozinkaSNasimSlovimaVraca400AUmjesto500() throws Exception {
		registruj("dzenan@example.com", "č".repeat(37), "Dženan")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.greske[0].polje").value("password"));
	}

	@Test
	void neispravniPodaciVracajuGreskuZaSvakoPolje() throws Exception {
		registruj("nije-email", "kratka", " ")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.greske.length()").value(3));
	}

}
