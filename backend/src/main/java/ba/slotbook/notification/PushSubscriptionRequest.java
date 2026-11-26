package ba.slotbook.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Oblik je isti kao PushSubscription.toJSON() u browseru, pa frontend šalje ono što dobije, plus naziv uređaja
public record PushSubscriptionRequest(
		@NotBlank(message = "Nedostaje adresa push servisa.")
		@Size(max = 1000, message = "Adresa push servisa je preduga.")
		String endpoint,
		@NotNull(message = "Nedostaju ključevi pretplate.") @Valid Keys keys,
		@Size(max = 100, message = "Naziv uređaja može imati najviše 100 znakova.") String device) {

	public record Keys(
			@NotBlank(message = "Nedostaje ključ p256dh.") @Size(max = 200, message = "Ključ p256dh je predug.") String p256dh,
			@NotBlank(message = "Nedostaje ključ auth.") @Size(max = 100, message = "Ključ auth je predug.") String auth) {
	}

}
