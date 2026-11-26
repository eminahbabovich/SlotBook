package ba.slotbook.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProvjeraLozinkeTest {

	@ParameterizedTest
	@ValueSource(strings = { "12345678", "123456789", "kratka7" })
	void kraceOdDesetZnakovaPada(String lozinka) {
		assertThat(ProvjeraLozinke.problem(lozinka)).isEqualTo("Lozinka treba imati najmanje 10 znakova.");
	}

	// Svaka bi prošla samo dužinu. Pala bi kad bi se poredilo bez malih slova ili bez obrnutog niza.
	@ParameterizedTest
	@ValueSource(strings = { "1234567890", "0987654321", "QWERTZUIOP", "asdfghjklč", "abcdefghijk",
			"1qaz2wsx3edc", "12345678901234" })
	void nizSTastatureIliRedomPada(String lozinka) {
		assertThat(ProvjeraLozinke.problem(lozinka)).contains("niz s tastature");
	}

	@ParameterizedTest
	@ValueSource(strings = { "aaaaaaaaaaaa", "abababababab", "1111111111" })
	void istiZnakoviKojiSePonavljajuPadaju(String lozinka) {
		assertThat(ProvjeraLozinke.problem(lozinka)).contains("ponavljaju");
	}

	// "Lozinka2024!" pada zbog osnove bez broja na kraju: bez skidanja cifara bi prošla
	@ParameterizedTest
	@ValueSource(strings = { "lozinkalozinka", "Password1234", "Lozinka2024!", "sarajevo1984", "!!Zeljeznicar99" })
	void cestaLozinkaIliCestaRijecSBrojemPada(String lozinka) {
		assertThat(ProvjeraLozinke.problem(lozinka)).contains("ljudi često koriste");
	}

	@ParameterizedTest
	@ValueSource(strings = { "kozija-cuprija-1580", "igman-1984", "trebević1984", "tri žute mačke" })
	void lozinkeKakveTestoviIKorisniciPisuProlaze(String lozinka) {
		assertThat(ProvjeraLozinke.problem(lozinka)).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = { "amra.hodzic2001", "XAMRA.HODZICx" })
	void lozinkaSDijelomEmailaPrijeMajmunskogA(String lozinka) {
		assertThat(ProvjeraLozinke.sadrziEmail(lozinka, "amra.hodzic@example.com")).isTrue();
		assertThat(ProvjeraLozinke.sadrziEmail("kozija-cuprija-1580", "amra.hodzic@example.com")).isFalse();
	}

}
