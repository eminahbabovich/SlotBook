package ba.slotbook.resource;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record ChangeProviderRequest(
		@NotNull(message = "Izaberi osobu koja vodi resurs.")
		UUID providerId) {
}
