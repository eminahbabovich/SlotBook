package ba.slotbook.status;

import java.time.Instant;

public record StatusResponse(String aplikacija, Instant vrijeme) {
}
