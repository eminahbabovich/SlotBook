package ba.slotbook.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import ba.slotbook.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
		"DB_URL=jdbc:postgresql://baza.example.com:5432/slotbook?sslmode=require",
		"DB_USER=korisnik",
		"DB_PASSWORD=lozinka",
		"PORT=9090"
})
@ActiveProfiles("prod")
class ProdProfilTest {

	@Autowired
	Environment env;

	@Test
	void bazaSeUProdProfiluSpajaPrekoDbVarijabli() {
		assertThat(env.getProperty("spring.datasource.url"))
				.isEqualTo("jdbc:postgresql://baza.example.com:5432/slotbook?sslmode=require");
		assertThat(env.getProperty("spring.datasource.username")).isEqualTo("korisnik");
		assertThat(env.getProperty("spring.datasource.password")).isEqualTo("lozinka");
	}

	@Test
	void portDolaziIzVarijableOkruzenja() {
		assertThat(env.getProperty("server.port")).isEqualTo("9090");
	}

}
