package ba.slotbook.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class CheckInTokenTest {

	private static final String TAJNA = "tajna-samo-za-test-sarajevo-1984-olimpijada";

	private final CheckInToken tokeni = new CheckInToken(TAJNA);
	private final UUID rezervacija = UUID.fromString("3f1c2a9e-7b4d-4e8a-9c21-5d6e7f809a1b");

	@Test
	void tokenRezervacijeVaziZaTuRezervacijuIUvijekJeIsti() {
		String token = tokeni.za(rezervacija);

		assertThat(tokeni.vazi(rezervacija, token)).isTrue();
		assertThat(new CheckInToken(TAJNA).za(rezervacija)).isEqualTo(token);
		assertThat(token).matches("[A-Za-z0-9_-]{43}");
	}

	@Test
	void tokenJedneRezervacijeNeVaziZaDrugu() {
		UUID druga = UUID.fromString("9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d");

		assertThat(tokeni.vazi(druga, tokeni.za(rezervacija))).isFalse();
	}

	// Ključ je izveden iz JWT tajne, pa token s drugog servera (druga tajna) ne vrijedi ovdje
	@Test
	void tokenPodDrugomTajnomNeVazi() {
		String tudji = new CheckInToken("neka-druga-tajna-iz-drugog-okruzenja-2026").za(rezervacija);

		assertThat(tokeni.vazi(rezervacija, tudji)).isFalse();
	}

	@Test
	void prazanSkracenIliNullTokenNeVazi() {
		String token = tokeni.za(rezervacija);

		assertThat(tokeni.vazi(rezervacija, null)).isFalse();
		assertThat(tokeni.vazi(rezervacija, "")).isFalse();
		assertThat(tokeni.vazi(rezervacija, token.substring(0, 42))).isFalse();
		assertThat(tokeni.vazi(rezervacija, token + "x")).isFalse();
	}

}
