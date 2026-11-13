package ba.slotbook.availability;

import java.util.List;

// Zona ide uz pravila, da niko ne pomisli da je 08:00 u UTC-u
public record AvailabilityResponse(String timezone, List<AvailabilityInterval> rules) {
}
