package ba.slotbook.availability;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Omotač oko liste, a ne gola lista u tijelu: tako greške dolaze kao rules[0].start, u istom
// obliku kao ostale greške validacije. Prazna lista znači da resurs nema radno vrijeme.
public record AvailabilityRequest(
		@NotNull(message = "Pošalji listu intervala. Prazna lista znači da resurs ne radi.")
		@Size(max = 50, message = "Sedmica može imati najviše 50 intervala.")
		@Valid
		List<AvailabilityInterval> rules) {
}
