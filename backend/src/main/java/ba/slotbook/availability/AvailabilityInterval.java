package ba.slotbook.availability;

import java.time.DayOfWeek;
import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;

// Isti oblik u zahtjevu i u odgovoru: {"dayOfWeek": "MONDAY", "start": "08:00", "end": "12:00"}
public record AvailabilityInterval(
		@NotNull(message = "Izaberi dan, npr. MONDAY.")
		DayOfWeek dayOfWeek,

		@NotNull(message = "Unesi početak, npr. 08:00.")
		LocalTime start,

		@NotNull(message = "Unesi kraj, npr. 16:00.")
		LocalTime end) {
}
