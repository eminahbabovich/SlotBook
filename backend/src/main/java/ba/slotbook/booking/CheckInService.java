package ba.slotbook.booking;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import ba.slotbook.error.InvalidFieldException;

// QR check-in. Prelaz u COMPLETED je isti kao kod ručnog dolaska, ovdje je samo dokaz da je
// klijent stvarno pokazao svoj kod.
@Service
public class CheckInService {

	private final CheckInToken tokeni;
	private final BookingService bookingService;
	private final String appUrl;

	public CheckInService(CheckInToken tokeni, BookingService bookingService,
			@Value("${slotbook.app-url}") String appUrl) {
		this.tokeni = tokeni;
		this.bookingService = bookingService;
		this.appUrl = appUrl;
	}

	public CheckInLinkResponse link(UUID bookingId) {
		return new CheckInLinkResponse(appUrl + "/check-in/" + bookingId + "#k=" + tokeni.za(bookingId));
	}

	// Token se provjerava prije bilo kakvog čitanja iz baze: pogrešan kod ne mijenja ništa
	public BookingResponse checkIn(UUID bookingId, UUID actorId, String token) {
		if (!tokeni.vazi(bookingId, token)) {
			throw new InvalidFieldException("token", "Ovaj QR kod ne pripada ovoj rezervaciji.");
		}
		return bookingService.changeStatus(bookingId, actorId, BookingStatus.COMPLETED);
	}

}
