package ba.slotbook.screen;

import java.util.UUID;

// Ekran je ugašen ili zamijenjen: otvorene veze tog ekrana se zatvaraju poslije commita
record DoorScreenRevoked(UUID screenId) {
}
