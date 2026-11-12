package ba.slotbook.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class UserTest {

	@Test
	void emailSeSpremaMalimSlovimaIBezRazmaka() {
		User korisnik = new User("  Amra.Hodzic@Example.COM ", "hash", "Amra Hodžić", Instant.EPOCH);

		assertThat(korisnik.getEmail()).isEqualTo("amra.hodzic@example.com");
	}

	@Test
	void emailJeIstiINaServeruSaTurskimLocaleom() {
		Locale prije = Locale.getDefault();
		Locale.setDefault(Locale.forLanguageTag("tr-TR"));
		try {
			assertThat(User.normalizeEmail("IRMA@example.com")).isEqualTo("irma@example.com");
		} finally {
			Locale.setDefault(prije);
		}
	}

}
