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
import ba.slotbook.resource.ResourceNotFoundException;
import ba.slotbook.resource.ResourceRepository;
import ba.slotbook.user.User;
import ba.slotbook.user.UserRepository;

@Service
public class BookingService {

	// SQLSTATE za prekršen EXCLUDE constraint. Jedini takav na bookings je bookings_bez_preklapanja.
	private static final String EXCLUSION_VIOLATION = "23P01";

	private final BookingRepository bookings;
	private final BookingEventRepository events;
	private final ResourceRepository resources;
	private final UserRepository users;
	private final SlotService slotService;
	private final Clock clock;

	public BookingService(BookingRepository bookings, BookingEventRepository events, ResourceRepository resources,
			UserRepository users, SlotService slotService, Clock clock) {
		this.bookings = bookings;
		this.events = events;
		this.resources = resources;
		this.users = users;
		this.slotService = slotService;
		this.clock = clock;
	}

	// Ko smije rezervisati provjerava @PreAuthorize u kontroleru. Klijent šalje samo početak, a kraj
	// se uzima iz ponuđenog termina, pa rezervacija ne može biti duža ni pomjerena od termina u kalendaru.
	@Transactional
	public BookingResponse create(UUID resourceId, UUID clientId, Instant start) {
		// Rezervacije istog resursa idu jedna za drugom. Bez ovoga dva istovremena upisa u EXCLUDE
		// čekaju jedan na drugog, Postgres to prekine kao deadlock (40P01) tek poslije sekunde, a
		// klijent dobije 500. Ovako drugi vidi commitan red i dobije 23P01. Preklapanje i dalje čuva baza.
		Resource resurs = resources.findLockedById(resourceId).orElseThrow(ResourceNotFoundException::new);
		TimeRange termin = slotService.offered(resourceId, start)
				.orElseThrow(() -> new InvalidFieldException("startTime",
						"Ovaj termin se ne nudi. Izaberi jedan od slobodnih termina u kalendaru."));
		BookingStatus status = resurs.isAutoConfirm() ? BookingStatus.CONFIRMED : BookingStatus.PENDING;
		User klijent = users.getReferenceById(clientId);
		Instant sada = clock.instant();
		Booking rezervacija;
		try {
			rezervacija = bookings.saveAndFlush(new Booking(resurs, klijent, termin.start(), termin.end(), status, sada));
		} catch (DataIntegrityViolationException e) {
			// Ostale greške integriteta nisu zauzet termin i ne smiju se tako prijaviti korisniku
			if (e.getMostSpecificCause() instanceof SQLException sql
					&& EXCLUSION_VIOLATION.equals(sql.getSQLState())) {
				throw new SlotTakenException();
			}
			throw e;
		}
		// Nastanak je prvi prelaz stanja. Ista transakcija: ako ovaj upis padne, nema ni rezervacije.
		events.save(new BookingEvent(rezervacija, null, status, klijent, sada));
		return BookingResponse.from(rezervacija);
	}

}
