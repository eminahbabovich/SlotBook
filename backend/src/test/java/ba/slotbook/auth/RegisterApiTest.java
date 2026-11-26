package ba.slotbook.auth;

import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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

	@Autowired
	JdbcTemplate jdbc;

	private ResultActions registruj(String email, String lozinka, String ime) throws Exception {
		return mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "%s", "password": "%s", "displayName": "%s"}
						""".formatted(email, lozinka, ime)));
	}

	private long mailova(String email, String vrsta) {
		return jdbc.queryForObject("select count(*) from notifications n join users u on u.id = n.user_id "
				+ "where u.email = ? and n.type = ?", Long.class, email, vrsta);
	}

	// Bez tijela: odgovor ne smije zavisiti od toga da li je nalog već postojao
	@Test
	void registracijaVraca202BezTijelaIPraviNalogSLinkomZaPotvrdu() throws Exception {
		registruj("Lejla.Begic@Example.com", "mostarskiMost1", "Lejla Begić")
				.andExpect(status().isAccepted())
				.andExpect(content().string(""));

		User user = users.findByEmail("lejla.begic@example.com").orElseThrow();
		assertThat(user.getDisplayName()).isEqualTo("Lejla Begić");
		assertThat(user.isEmailVerified()).isFalse();
		assertThat(mailova("lejla.begic@example.com", "EMAIL_VERIFICATION")).isEqualTo(1);
	}

	@Test
	void lozinkaSeUBaziCuvaKaoBcryptHash() throws Exception {
		registruj("haris@example.com", "bascarsija99", "Haris").andExpect(status().isAccepted());

		User user = users.findByEmail("haris@example.com").orElseThrow();
		assertThat(user.getPasswordHash()).startsWith("$2a$").doesNotContain("bascarsija99");
	}

	// Isti odgovor kao za novi nalog, a vlasnik dobije mail da je neko pokušao. Postojeći nalog se ne
	// mijenja: ni lozinka ni ime iz drugog pokušaja ne ulaze.
	@Test
	void postojeciEmailDobijaIstiOdgovorAVlasnikMail() throws Exception {
		registruj("selma@example.com", "trebevic123", "Selma").andExpect(status().isAccepted());
		jdbc.update("update users set email_verified_at = now() where email = 'selma@example.com'");
		String hash = users.findByEmail("selma@example.com").orElseThrow().getPasswordHash();

		registruj("SELMA@example.com", "drugaLozinka1", "Selma druga")
				.andExpect(status().isAccepted())
				.andExpect(content().string(""));

		User selma = users.findByEmail("selma@example.com").orElseThrow();
		assertThat(selma.getPasswordHash()).isEqualTo(hash);
		assertThat(selma.getDisplayName()).isEqualTo("Selma");
		assertThat(mailova("selma@example.com", "ACCOUNT_EXISTS")).isEqualTo(1);
		assertThat(mailova("selma@example.com", "EMAIL_VERIFICATION")).isEqualTo(1);
	}

	// Ko se registrovao pa nije potvrdio email, drugi put dobije novi link umjesto maila "nalog postoji"
	@Test
	void postojeciNepotvrdjenEmailDobijaNoviLink() throws Exception {
		registruj("irma@example.com", "vrelo-bosne1", "Irma").andExpect(status().isAccepted());

		registruj("irma@example.com", "vrelo-bosne1", "Irma").andExpect(status().isAccepted());

		assertThat(mailova("irma@example.com", "EMAIL_VERIFICATION")).isEqualTo(2);
		assertThat(mailova("irma@example.com", "ACCOUNT_EXISTS")).isZero();
	}

	@Test
	void mailONalogKojiPostojiImaGranicuOdTriNaSat() throws Exception {
		registruj("kenan@example.com", "igman-zimi1984", "Kenan").andExpect(status().isAccepted());
		jdbc.update("update users set email_verified_at = now() where email = 'kenan@example.com'");

		for (int i = 0; i < 4; i++) {
			registruj("kenan@example.com", "nesto-drugo-1", "Neko").andExpect(status().isAccepted());
		}

		assertThat(mailova("kenan@example.com", "ACCOUNT_EXISTS")).isEqualTo(3);
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


	// 12345678 je bila dovoljna dok je pravilo bilo samo osam znakova
	@Test
	void cestaLozinkaSeOdbijaSPorukomKojaKazeSta() throws Exception {
		registruj("dzenana.lozinka@example.com", "sarajevo2024", "Dženana")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.greske[0].polje").value("password"))
				.andExpect(jsonPath("$.greske[0].poruka").value(containsString("često koriste")));
		assertThat(jdbc.queryForObject("select count(*) from users where email = ?", Long.class,
				"dzenana.lozinka@example.com")).isZero();
	}

	// Isti 400 i za email koji već ima nalog. Da se provjera radi tek za nov nalog, postojeći bi dobio
	// 202 i odgovor bi otkrio ko je registrovan.
	@Test
	void lozinkaSEmailomPadaIsto() throws Exception {
		registruj("emir.postojeci@example.com", "kozija-cuprija-1580", "Emir").andExpect(status().isAccepted());

		registruj("emir.postojeci@example.com", "emir.postojeci-77", "Emir")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.greske[0].poruka").value("Lozinka ne smije sadržati tvoj email."));
		registruj("emir.novi@example.com", "emir.novi-1580", "Emir")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.greske[0].poruka").value("Lozinka ne smije sadržati tvoj email."));
	}

}
