package ba.slotbook.organization;

import java.util.UUID;

// alreadyMember: osoba je već bila član, pa link nije potrošen. Nije greška, nego poruka.
public record JoinedResponse(UUID organizationId, String slug, String name, boolean alreadyMember) {
}
