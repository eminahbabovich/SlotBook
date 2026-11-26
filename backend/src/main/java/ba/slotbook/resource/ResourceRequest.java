package ba.slotbook.resource;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Granice su iste kao CHECK u V6__resources.sql, da korisnik dobije 400 s porukom, a ne 500 iz baze
public record ResourceRequest(
		@NotBlank(message = "Unesi naziv resursa, npr. Sala za funkcionalni trening.")
		@Size(max = 100, message = "Naziv može imati najviše 100 znakova.")
		String name,

		@Size(max = 1000, message = "Opis može imati najviše 1000 znakova.")
		String description,

		@NotNull(message = "Unesi trajanje termina u minutama.")
		@Min(value = 5, message = "Termin traje najmanje 5 minuta.")
		@Max(value = 480, message = "Termin traje najviše 480 minuta (8 sati).")
		Integer slotMinutes,

		// Ko ne pošalje polje, dobije automatsku potvrdu, i kod PUT-a, kao što opis bez vrijednosti postane prazan
		Boolean autoConfirm,

		// Bez polja nema roka, klijent otkazuje do početka termina
		@Min(value = 0, message = "Rok za otkazivanje ne može biti negativan. Za otkazivanje bez roka pošalji 0.")
		@Max(value = 168, message = "Rok za otkazivanje može biti najviše 168 sati (sedmica).")
		Integer cancelNoticeHours,

		// Bez polja je jedno mjesto, kao običan resurs. Gornja granica je ista kao CHECK u V37.
		@Min(value = 1, message = "Termin ima najmanje jedno mjesto.")
		@Max(value = 100, message = "Termin može imati najviše 100 mjesta.")
		Integer capacity,

		// Bez polja je besplatno. U feninzima, granice su iste kao CHECK u V38.
		@Min(value = 0, message = "Cijena ne može biti negativna. Za besplatan termin pošalji 0.")
		@Max(value = 100000, message = "Cijena termina može biti najviše 1000 KM (100000 feninga).")
		Integer priceCents,
		// Pravila korištenja, sva opciona. Granice su iste kao CHECK u V43.
		@Min(value = 1, message = "Najmanje jedna rezervacija po članu. Bez ograničenja ostavi polje prazno.")
		@Max(value = 100, message = "Najviše 100 budućih rezervacija po članu.")
		Integer maxActivePerMember,
		@Min(value = 1, message = "Rezerviše se najmanje dan unaprijed. Bez ograničenja ostavi polje prazno.")
		@Max(value = 365, message = "Najviše 365 dana unaprijed.")
		Integer maxDaysAhead,
		@Min(value = 1, message = "Najmanje minut prije početka. Bez ograničenja ostavi polje prazno.")
		@Max(value = 10080, message = "Najviše 10080 minuta (sedmica) prije početka.")
		Integer minNoticeMinutes) {

	public ResourceRequest {
		autoConfirm = autoConfirm == null ? Boolean.TRUE : autoConfirm;
		cancelNoticeHours = cancelNoticeHours == null ? 0 : cancelNoticeHours;
		capacity = capacity == null ? 1 : capacity;
		priceCents = priceCents == null ? 0 : priceCents;
	}

	ResourceRules pravila() {
		return new ResourceRules(maxActivePerMember, maxDaysAhead, minNoticeMinutes);
	}

}
