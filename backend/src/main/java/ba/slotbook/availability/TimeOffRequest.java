package ba.slotbook.availability;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// OffsetDateTime, ne LocalDateTime: "2026-12-24T00:00" bez pomaka se odbija, jer se ne zna
// da li je to ponoć u Sarajevu ili u UTC-u. U bazu ide kao trenutak (Instant).
public record TimeOffRequest(
		@NotNull(message = "Unesi početak s pomakom, npr. 2026-12-24T00:00:00+01:00.")
		OffsetDateTime startsAt,

		@NotNull(message = "Unesi kraj s pomakom, npr. 2026-12-27T00:00:00+01:00.")
		OffsetDateTime endsAt,

		@Size(max = 200, message = "Razlog može imati najviše 200 znakova.")
		String reason) {
}
