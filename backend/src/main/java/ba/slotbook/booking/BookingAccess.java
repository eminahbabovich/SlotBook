package ba.slotbook.booking;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import ba.slotbook.resource.ResourceAccess;

// Poziva se iz @PreAuthorize kao @bookingAccess.canManage(...). Resurs, pa i organizacija, uzima
// se iz rezervacije, a ne iz zahtjeva, isto kao kod resursa.
@Component
public class BookingAccess {

	private final BookingRepository bookings;
	private final ResourceAccess resourceAccess;

	public BookingAccess(BookingRepository bookings, ResourceAccess resourceAccess) {
		this.bookings = bookings;
		this.resourceAccess = resourceAccess;
	}

	// Potvrdu, odbijanje, dolazak i nedolazak radi ko vodi resurs: admin ili provider tog resursa
	public boolean canManage(UUID bookingId, Authentication authentication) {
		return bookings.findById(bookingId)
				.map(rezervacija -> resourceAccess.canManage(rezervacija.getResource().getId(), authentication))
				.orElse(false);
	}

	// Klijent vidi i otkazuje svoju rezervaciju, a admin i provider bilo koju na resursu koji vode
	public boolean canView(UUID bookingId, Authentication authentication) {
		if (!(authentication instanceof JwtAuthenticationToken jwt)) {
			return false;
		}
		UUID korisnikId = UUID.fromString(jwt.getToken().getSubject());
		return bookings.findById(bookingId)
				.map(rezervacija -> rezervacija.getClient().getId().equals(korisnikId)
						|| resourceAccess.canManage(rezervacija.getResource().getId(), authentication))
				.orElse(false);
	}

}
