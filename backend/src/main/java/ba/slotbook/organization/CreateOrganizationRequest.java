package ba.slotbook.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateOrganizationRequest(
		@NotBlank(message = "Unesi naziv organizacije.")
		@Size(max = 100, message = "Naziv može imati najviše 100 znakova.")
		String name,

		@NotBlank(message = "Unesi adresu organizacije.")
		@Size(min = 3, max = 60, message = "Adresa treba imati od 3 do 60 znakova.")
		@Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
				message = "Adresa smije imati samo mala slova bez kvačica, brojeve i crtice između riječi, npr. citaonica-fit.")
		String slug,

		@NotBlank(message = "Izaberi vremensku zonu.")
		@RegionTimeZone(message = "Zona mora biti naziv poput Europe/Sarajevo, a ne pomak poput +01:00.")
		String timezone) {
}
