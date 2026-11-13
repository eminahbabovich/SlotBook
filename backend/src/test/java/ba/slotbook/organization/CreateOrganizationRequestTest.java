package ba.slotbook.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

class CreateOrganizationRequestTest {

	private static final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();

	@AfterAll
	static void zatvori() {
		factory.close();
	}

	private Set<ConstraintViolation<CreateOrganizationRequest>> provjeri(String zona) {
		return factory.getValidator().validate(new CreateOrganizationRequest("Čitaonica FIT", "citaonica-fit", zona));
	}

	// Tokio i Kolkata danas nemaju ljetno vrijeme, ali su stvarna mjesta i moraju proći
	@ParameterizedTest
	@ValueSource(strings = { "Europe/Sarajevo", "Europe/Belgrade", "Asia/Tokyo", "Asia/Kolkata" })
	void zonaMjestaProlazi(String zona) {
		assertThat(provjeri(zona)).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "+01:00", "Etc/GMT-1", "UTC", "Etc/UTC", "Europe/Mostar" })
	void fiksniPomakINepoznataZonaSeOdbijaju(String zona) {
		assertThat(provjeri(zona))
				.extracting(greska -> greska.getPropertyPath().toString())
				.containsExactly("timezone");
	}

}
