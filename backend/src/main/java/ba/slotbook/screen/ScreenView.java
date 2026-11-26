package ba.slotbook.screen;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Javni prikaz ekrana, bez prijave. Namjerno bez ikakvih podataka o ljudima: ni imena, ni emaila, ni
// statusa tuđe rezervacije. Termin je samo slobodan (s brojem mjesta) ili pun.
// now je vrijeme servera, da tablet s krivim satom ne pokaže pogrešan termin kao trenutni.
public record ScreenView(String organizationName, String timezone, LocalDate date, Instant now, String inviteUrl,
		List<ScreenResource> resources) {

	public record ScreenResource(UUID id, String name, int capacity, List<ScreenSlot> slots) {
	}

	// seatsLeft 0 znači pun termin
	public record ScreenSlot(Instant start, Instant end, int seatsLeft) {
	}

}
