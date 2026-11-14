package ba.slotbook.booking;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.availability.SlotService;
import ba.slotbook.availability.TimeRange;
import ba.slotbook.error.InvalidFieldException;
import ba.slotbook.resource.Resource;
import ba.slotbook.resource.ResourceRepository;
import ba.slotbook.user.UserRepository;

@Service
public class BookingService {

	// SQLSTATE za prekršen EXCLUDE constraint. Jedini takav na bookings je bookings_bez_preklapanja.
	private static final String EXCLUSION_VIOLATION = "23P01";

	private final BookingRepository bookings;
	private final ResourceRepository resources;
	private final UserRepository users;
	private final SlotService slotService;
	private final Clock clock;

	public BookingService(BookingRepository bookings, ResourceRepository resources, UserRepository users,
			SlotService slotService, Clock clock) {
		this.bookings = bookings;
		this.resources = resources;
		this.users = users;
		this.slotService = slotService;
		this.clock = clock;
	}

	// Ko smije rezervisati provjerava @PreAuthorize u kontroleru. Klijent šalje samo početak, a kraj
	// se uzima iz ponuđenog termina, pa rezervacija ne može biti duža ni pomjerena od termina u kalendaru.
	@Transactional
	public BookingResponse create(UUID resourceId, UUID clientId, Instant start) {
		TimeRange termin = slotService.offered(resourceId, start)
				.orElseThrow(() -> new InvalidFieldException("startTime",
						"Ovaj termin se ne nudi. Izaberi jedan od slobodnih termina u kalendaru."));
		Resource resurs = resources.getReferenceById(resourceId);
		BookingStatus status = resurs.isAutoConfirm() ? BookingStatus.CONFIRMED : BookingStatus.PENDING;
		try {
			return BookingResponse.from(bookings.saveAndFlush(new Booking(resurs, users.getReferenceById(clientId),
					termin.start(), termin.end(), status, clock.instant())));
		} catch (DataIntegrityViolationException e) {
			// Ostale greške integriteta nisu zauzet termin i ne smiju se tako prijaviti korisniku
			if (e.getMostSpecificCause() instanceof SQLException sql
					&& EXCLUSION_VIOLATION.equals(sql.getSQLState())) {
				throw new SlotTakenException();
			}
			throw e;
		}
	}

}
