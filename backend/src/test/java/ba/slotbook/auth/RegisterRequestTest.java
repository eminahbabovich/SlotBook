package ba.slotbook.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

class RegisterRequestTest {

	private static final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();

	@AfterAll
	static void zatvori() {
		factory.close();
	}

	private Set<ConstraintViolation<RegisterRequest>> provjeri(String lozinka) {
		return factory.getValidator().validate(new RegisterRequest("amra@example.com", lozinka, "Amra"));
	}

	// Četiri različita slova, a ne jedno ponovljeno: isti znak 72 puta odbija JakaLozinka, a ovaj test
	// mjeri samo granicu u bajtovima
	@Test
	void lozinkaOd72BajtaProlazi() {
		assertThat(provjeri("tri-zute-macke-na-mostu-".repeat(3))).isEmpty();
		assertThat(provjeri("čćšž".repeat(9))).isEmpty();
	}

	@Test
	void lozinkaOd37NasihSlovaIma74BajtaIOdbijaSe() {
		assertThat(provjeri("čćšž".repeat(9) + "đ"))
				.extracting(greska -> greska.getPropertyPath().toString())
				.containsExactly("password");
	}

}
