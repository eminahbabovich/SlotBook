package ba.slotbook.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.config.ScheduledTaskHolder;
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

	@Autowired
	ScheduledTaskHolder raspored;

	// U testovima je slanje isključeno (application-default u test resursima), a u prod profilu mora raditi
	@Test
	void uProdukcijiSeObavijestiSalju() {
		assertThat(raspored.getScheduledTasks()).anyMatch(z -> z.toString().contains("NotificationJob.posalji"));
	}

	@Test
	void bazaSeUProdProfiluSpajaPrekoDbVarijabli() {
		assertThat(env.getProperty("spring.datasource.url"))
				.isEqualTo("jdbc:postgresql://baza.example.com:5432/slotbook?sslmode=require");
		assertThat(env.getProperty("spring.datasource.username")).isEqualTo("korisnik");
		assertThat(env.getProperty("spring.datasource.password")).isEqualTo("lozinka");
	}

	// Format sam (s requestId u JSON-u) provjerava RequestIdApiTest, ovdje samo da ga prod profil uključuje
	@Test
	void uProdukcijiJeLogJson() {
		assertThat(env.getProperty("logging.structured.format.console")).isEqualTo("ecs");
	}

	@Test
	void portDolaziIzVarijableOkruzenja() {
		assertThat(env.getProperty("server.port")).isEqualTo("9090");
	}

}
