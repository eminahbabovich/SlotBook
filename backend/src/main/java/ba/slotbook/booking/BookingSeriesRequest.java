package ba.slotbook.booking;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// Prva sedmica i broj sedmica, zajedno s prvom. Najviše 12, oko jednog semestra konsultacija.
public record BookingSeriesRequest(
		@NotNull(message = "Izaberi termin. Početak se šalje s pomakom, npr. 2035-11-19T09:00:00+01:00.")
		OffsetDateTime startTime,
		@NotNull(message = "Upiši koliko sedmica, od 2 do 12.")
		@Min(value = 2, message = "Serija ima najmanje 2 sedmice. Za jedan termin je obična rezervacija.")
		@Max(value = 12, message = "Serija ima najviše 12 sedmica.")
		Integer weeks) {
}
