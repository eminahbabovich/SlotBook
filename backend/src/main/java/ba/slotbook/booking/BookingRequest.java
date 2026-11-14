package ba.slotbook.booking;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;

// Samo početak: kraj određuje trajanje termina na resursu. S pomakom, kao kod izuzetaka,
// jer "09:00" bez pomaka ne kaže da li je to 09:00 u Sarajevu ili u UTC-u.
public record BookingRequest(
		@NotNull(message = "Izaberi termin. Početak se šalje s pomakom, npr. 2035-11-19T09:00:00+01:00.")
		OffsetDateTime startTime) {
}
