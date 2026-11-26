package ba.slotbook.notification;

import java.time.Instant;
import java.util.UUID;

public record PushSubscriptionResponse(UUID id, String device, Instant createdAt) {
}
