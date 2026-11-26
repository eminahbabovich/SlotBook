package ba.slotbook.screen;

import java.time.Instant;
import java.util.UUID;

// Za admina: živ ekran i njegova adresa. resourceId i resourceName su null za ekran cijele organizacije.
public record DoorScreenResponse(UUID id, UUID resourceId, String resourceName, String url, Instant createdAt) {
}
