package ba.slotbook.resource;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Granice su iste kao CHECK u V6__resources.sql, da korisnik dobije 400 s porukom, a ne 500 iz baze
public record CreateResourceRequest(
		@NotBlank(message = "Unesi naziv resursa, npr. Sala za funkcionalni trening.")
		@Size(max = 100, message = "Naziv može imati najviše 100 znakova.")
		String name,

		@Size(max = 1000, message = "Opis može imati najviše 1000 znakova.")
		String description,

		@NotNull(message = "Unesi trajanje termina u minutama.")
		@Min(value = 5, message = "Termin traje najmanje 5 minuta.")
		@Max(value = 480, message = "Termin traje najviše 480 minuta (8 sati).")
		Integer slotMinutes) {
}
