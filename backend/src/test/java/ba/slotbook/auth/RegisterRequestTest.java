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

	@Test
	void lozinkaOd72BajtaProlazi() {
		assertThat(provjeri("a".repeat(72))).isEmpty();
		assertThat(provjeri("č".repeat(36))).isEmpty();
	}

	@Test
	void lozinkaOd37SlovaCIma74BajtaIOdbijaSe() {
		assertThat(provjeri("č".repeat(37)))
				.extracting(greska -> greska.getPropertyPath().toString())
				.containsExactly("password");
	}

}
