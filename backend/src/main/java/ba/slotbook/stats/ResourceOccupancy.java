package ba.slotbook.stats;

import java.util.UUID;

// Obje brojke umjesto procenta: popunjenost može preći 100 % kad se radno vrijeme skrati poslije
// rezervacija (vidi docs/ODLUKE.md), a iz minuta se zbir za cijelu organizaciju računa tačno
public record ResourceOccupancy(UUID resourceId, String name, long bookedMinutes, long offeredMinutes) {
}
