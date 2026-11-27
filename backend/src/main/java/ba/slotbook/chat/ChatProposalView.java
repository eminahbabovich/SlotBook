package ba.slotbook.chat;

import java.time.Instant;
import java.util.UUID;

// Karta s dugmadima Potvrdi i Odustani. Vrijeme je Instant kao svugdje u API-ju, a widget ga prikaže u
// zoni organizacije. bookingId je rezervacija koja se otkazuje (CANCEL), za BOOK je null.
public record ChatProposalView(UUID id, String action, UUID resourceId, String resourceName, Instant startTime,
		Instant endTime, boolean needsProviderConfirmation, UUID bookingId) {
}
