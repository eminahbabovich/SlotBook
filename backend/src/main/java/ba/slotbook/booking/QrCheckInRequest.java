package ba.slotbook.booking;

import jakarta.validation.constraints.NotBlank;

public record QrCheckInRequest(@NotBlank(message = "Nema koda iz QR-a. Skeniraj kod ponovo.") String token) {
}
