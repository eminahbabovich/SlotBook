package ba.slotbook.chat;

import java.time.Instant;

// role je "user" ili "assistant"
public record ChatMessageResponse(String role, String text, Instant createdAt) {
}
