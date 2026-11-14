package ba.slotbook.booking;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.availability.SlotService;
import ba.slotbook.availability.TimeRange;
import ba.slotbook.error.InvalidFieldException;
import ba.slotbook.notification.NotificationService;
import ba.slotbook.notification.NotificationType;
import ba.slotbook.paging.PageResponse;
import ba.slotbook.resource.Resource;
import ba.slotbook.resource.ResourceNotFoundException;
import ba.slotbook.resource.ResourceRepository;
import ba.slotbook.user.User;
import ba.slotbook.user.UserRepository;

@Service
public class BookingService {

	// SQLSTATE za prekršen EXCLUDE constraint. Jedini takav na bookings je bookings_bez_preklapanja.
	private static final String EXCLUSION_VIOLATION = "23P01";
	// Prekršen UNIQUE. Na bookings je to samo (client_id, idempotency_key), jer UUID ključ generiše Hibernate.
	private static final String UNIQUE_VIOLATION = "23505";
	private static final int NAJDUZI_KLJUC = 100;

	private final BookingRepository bookings;
	private final BookingEventRepository events;
	private final ResourceRepository resources;
	private final UserRepository users;
	private final SlotService slotService;
	private final NotificationService notifications;
	private final Clock clock;
	private final Duration rokZaPotvrdu;
	private final Duration zatvaranjePoslije;

	public BookingService(BookingRepository bookings, BookingEventRepository events, ResourceRepository resources,
			UserRepository users, SlotService slotService, NotificationService notifications, Clock clock,
			@Value("${slotbook.booking.pending-hours}") int satiZaPotvrdu,
			@Value("${slotbook.booking.complete-after-hours}") int satiDoZatvaranja) {
		this.bookings = bookings;
		this.events = events;
		this.resources = resources;
		this.users = users;
		this.slotService = slotService;
		this.notifications = notifications;
		this.clock = clock;
		this.rokZaPotvrdu = Duration.ofHours(satiZaPotvrdu);
		this.zatvaranjePoslije = Duration.ofHours(satiDoZatvaranja);
	}

	// Ko smije rezervisati provjerava @PreAuthorize u kontroleru. Klijent šalje samo početak, a kraj
	// se uzima iz ponuđenog termina, pa rezervacija ne može biti duža ni pomjerena od termina u kalendaru.
	@Transactional
	public BookingResponse create(UUID resourceId, UUID clientId, Instant start, String idempotencyKey) {
		if (idempotencyKey != null && (idempotencyKey.isBlank() || idempotencyKey.length() > NAJDUZI_KLJUC)) {
			throw new InvalidFieldException("Idempotency-Key", "Ključ mora imati od 1 do " + NAJDUZI_KLJUC
					+ " znakova. Najbolje je poslati novi UUID za svaku rezervaciju.");
		}
		// Rezervacije istog resursa idu jedna za drugom. Bez ovoga dva istovremena upisa u EXCLUDE
		// čekaju jedan na drugog, Postgres to prekine kao deadlock (40P01) tek poslije sekunde, a
		// klijent dobije 500. Ovako drugi vidi commitan red i dobije 23P01. Preklapanje i dalje čuva baza.
		Resource resurs = resources.findLockedById(resourceId).orElseThrow(ResourceNotFoundException::new);
		Instant sada = clock.instant();
		// Istekla PENDING još drži termin dok je posao ne obradi (Render uspava servis, pa posao kasni),
		// a EXCLUDE bi novu rezervaciju odbio. Pod lockom resursa ih se ovdje oslobodi odmah.
		javiIsteklim(bookings.expireOnResource(resourceId, sada));
		// Tek poslije locka: dupli klik čeka da prvi zahtjev završi, pa ovdje nađe njegovu rezervaciju
		if (idempotencyKey != null) {
			var ranija = bookings.findByClientIdAndIdempotencyKey(clientId, idempotencyKey);
			if (ranija.isPresent()) {
				return ponovljen(ranija.get(), resourceId, start);
			}
		}
		TimeRange termin = slotService.offered(resourceId, start)
				.orElseThrow(() -> new InvalidFieldException("startTime",
						"Ovaj termin se ne nudi. Izaberi jedan od slobodnih termina u kalendaru."));
		BookingStatus status = resurs.isAutoConfirm() ? BookingStatus.CONFIRMED : BookingStatus.PENDING;
		User klijent = users.getReferenceById(clientId);
		Booking rezervacija;
		try {
			rezervacija = bookings.saveAndFlush(
					new Booking(resurs, klijent, termin.start(), termin.end(), status, sada, idempotencyKey,
							rokZaPotvrdu));
		} catch (DataIntegrityViolationException e) {
			// Ostale greške integriteta nisu zauzet termin i ne smiju se tako prijaviti korisniku
			if (e.getMostSpecificCause() instanceof SQLException sql) {
				if (EXCLUSION_VIOLATION.equals(sql.getSQLState())) {
					throw new SlotTakenException();
				}
				// Isti ključ u istom trenutku za drugi resurs: lock resursa ih nije poredao, pa odlučuje UNIQUE
				if (UNIQUE_VIOLATION.equals(sql.getSQLState())) {
					throw new IdempotencyKeyReusedException();
				}
			}
			throw e;
		}
		// Nastanak je prvi prelaz stanja. Ista transakcija: ako ovaj upis padne, nema ni rezervacije.
		events.save(new BookingEvent(rezervacija, null, status, klijent, sada));
		obavijesti(rezervacija, clientId);
		return BookingResponse.from(rezervacija);
	}

	// Svaka promjena statusa ide ovuda. Pravilo prelaza čuva entitet, istovremenu izmjenu @Version,
	// a zapis u historiji je u istoj transakciji. Postojanje i pristup je provjerio @PreAuthorize,
	// a rezervacije se ne brišu.
	@Transactional
	public BookingResponse changeStatus(UUID bookingId, UUID actorId, BookingStatus novi) {
		Booking rezervacija = bookings.findById(bookingId).orElseThrow();
		BookingStatus stari = rezervacija.getStatus();
		Instant sada = clock.instant();
		rezervacija.prijedi(novi, sada);
		return zapisi(rezervacija, stari, actorId, sada);
	}

	// Rok za otkazivanje važi za klijenta. Ko vodi resurs otkazuje i u zadnji čas (bolest, kvar).
	@Transactional
	public BookingResponse cancel(UUID bookingId, UUID actorId, boolean vodiResurs) {
		Booking rezervacija = bookings.findById(bookingId).orElseThrow();
		BookingStatus stari = rezervacija.getStatus();
		Instant sada = clock.instant();
		if (vodiResurs) {
			rezervacija.prijedi(BookingStatus.CANCELLED, sada);
		} else {
			rezervacija.otkaziKaoKlijent(sada);
		}
		return zapisi(rezervacija, stari, actorId, sada);
	}

	// Za BookingJob. Vraća broj isteklih, samo za log.
	@Transactional
	public int expireOverdue() {
		List<ExpiredBooking> istekle = bookings.expireAll(clock.instant());
		javiIsteklim(istekle);
		return istekle.size();
	}

	// Ne odmah na kraju termina: provider ima još dan da označi nedolazak, a COMPLETED je konačno
	@Transactional
	public int completeEnded() {
		Instant sada = clock.instant();
		return bookings.completeEndedBefore(sada.minus(zatvaranjePoslije), sada);
	}

	// Najkasniji termin prvo, pa budući termini idu prije prošlih. Sort bira server, kao kod resursa,
	// a id razdvaja rezervacije s istim početkom (različiti resursi), da nijedna ne preskoči stranicu.
	@Transactional(readOnly = true)
	public PageResponse<MyBookingResponse> mine(UUID clientId, Pageable pageable) {
		Pageable poPocetku = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
				Sort.by(Sort.Order.desc("startTime"), Sort.Order.asc("id")));
		return PageResponse.from(bookings.findMine(clientId, poPocetku).map(MyBookingResponse::from));
	}

	// Historija jedne rezervacije je kratka (nastanak i par prelaza), pa nema stranica
	@Transactional(readOnly = true)
	public List<BookingEventResponse> history(UUID bookingId) {
		return events.findHistory(bookingId).stream().map(BookingEventResponse::from).toList();
	}

	private BookingResponse zapisi(Booking rezervacija, BookingStatus stari, UUID actorId, Instant sada) {
		events.save(new BookingEvent(rezervacija, stari, rezervacija.getStatus(), users.getReferenceById(actorId),
				sada));
		obavijesti(rezervacija, actorId);
		return BookingResponse.from(rezervacija);
	}

	// Ko treba znati za novi status. U istoj transakciji kao promjena (outbox), a šalje se kasnije.
	// Dolazak, nedolazak i zatvaranje ne šalju ništa: klijent je tu bio ili nije, i to zna.
	private void obavijesti(Booking rezervacija, UUID actorId) {
		UUID klijent = rezervacija.getClient().getId();
		UUID provider = rezervacija.getResource().getProvider().getId();
		UUID id = rezervacija.getId();
		switch (rezervacija.getStatus()) {
			case PENDING -> notifications.enqueueForBooking(provider, NotificationType.BOOKING_AWAITS_CONFIRMATION, id);
			case CONFIRMED -> notifications.enqueueForBooking(klijent, NotificationType.BOOKING_CONFIRMED, id);
			case REJECTED -> notifications.enqueueForBooking(klijent, NotificationType.BOOKING_REJECTED, id);
			// Javlja se drugoj strani: kad otkaže klijent, provideru, a kad otkaže osoblje, klijentu
			case CANCELLED -> notifications.enqueueForBooking(actorId.equals(klijent) ? provider : klijent,
					NotificationType.BOOKING_CANCELLED, id);
			default -> {
			}
		}
	}

	private void javiIsteklim(List<ExpiredBooking> istekle) {
		for (ExpiredBooking istekla : istekle) {
			notifications.enqueueForBooking(istekla.getClientId(), NotificationType.BOOKING_EXPIRED, istekla.getId());
		}
	}

	// Isti ključ i isti zahtjev vraćaju raniju rezervaciju, u stanju u kojem je sada. Isti ključ
	// za drugi termin je greška klijenta (422 po nacrtu IETF-a za Idempotency-Key).
	private BookingResponse ponovljen(Booking ranija, UUID resourceId, Instant start) {
		if (!ranija.getResource().getId().equals(resourceId) || !ranija.getStartTime().equals(start)) {
			throw new IdempotencyKeyReusedException();
		}
		return BookingResponse.from(ranija);
	}

}
