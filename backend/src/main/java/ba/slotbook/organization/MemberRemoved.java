package ba.slotbook.organization;

import java.util.UUID;

// Objavljuje se u transakciji uklanjanja člana. Rezervacije i lista čekanja su u paketu booking, pa
// organizacija ne zove njihov servis, nego javi šta se desilo, a booking otkaže svoje (vidi
// BookingService.naUklonjenogClana). Slušalac je običan @EventListener: radi u istoj transakciji, pa
// ako otkazivanje padne, član ostaje.
public record MemberRemoved(UUID organizationId, UUID userId, UUID adminId) {
}
