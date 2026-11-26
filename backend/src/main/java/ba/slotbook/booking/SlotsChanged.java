package ba.slotbook.booking;

import java.util.UUID;

// Termini resursa su se promijenili: rezervacija, otkazivanje, istek, ponuda s liste čekanja.
// Nosi samo ID resursa. Šta se promijenilo, kalendar pita ponovo kroz /slots, s provjerom prava.
public record SlotsChanged(UUID resourceId) {
}
