package ba.slotbook.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.Test;

// Testovi rade sa snagom 4, pa nijedan drugi test ne bi primijetio da je i produkcija spuštena
class BcryptSnagaTest {

	@Test
	void produkcijaKoristiBcryptSnage10() throws Exception {
		Properties podesavanja = new Properties();
		try (InputStream fajl = getClass().getResourceAsStream("/application.properties")) {
			podesavanja.load(fajl);
		}

		assertThat(podesavanja.getProperty("slotbook.bcrypt-strength")).isEqualTo("10");
	}

}
