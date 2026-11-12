package ba.slotbook.error;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

// Postoji samo u testovima: pravih endpointa s validacijom još nemamo
@RestController
public class ProbniKontroler {

	public record Unos(
			@NotBlank(message = "Unesi ime.") String ime,
			@Min(value = 1, message = "Broj mora biti najmanje 1.") int broj) {
	}

	@PostMapping("/probno/validacija")
	void validacija(@Valid @RequestBody Unos unos) {
	}

	@GetMapping("/probno/greska")
	void greska() {
		throw new IllegalStateException("interna poruka koja ne smije izaci");
	}

	@GetMapping("/probno/zabranjeno")
	void zabranjeno() {
		throw new AccessDeniedException("nije član organizacije");
	}

}
