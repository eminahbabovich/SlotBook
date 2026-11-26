package ba.slotbook.notification;

import jakarta.validation.constraints.NotBlank;

// Koji browser: endpoint njegove pretplate, koji browser zna, a server po njemu nalazi pretplatu
public record PushEndpointRequest(@NotBlank(message = "Nedostaje adresa push servisa.") String endpoint) {
}
