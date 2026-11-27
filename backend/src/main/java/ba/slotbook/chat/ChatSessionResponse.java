package ba.slotbook.chat;

import java.time.Instant;
import java.util.UUID;

public record ChatSessionResponse(UUID id, Instant createdAt) {
}
