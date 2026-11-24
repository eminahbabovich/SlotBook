package ba.slotbook.booking;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Service
public class BookingService {

	// SQLSTATE za prekršen EXCLUDE constraint: isto mjesto ili ista osoba dvaput u istom vremenu (V37)
	private static final String EXCLUSION_VIOLATION = "23P01";
	// Prekršen UNIQUE. Na bookings je to samo (client_id, idempotency_key), jer UUID ključ generiše Hibernate.
	private static final String UNIQUE_VIOLATION = "23505";
	private static final int NAJDUZI_KLJUC = 100;

	private final BookingRepository bookings;
	private final WaitlistEntryRepository waitlist;
	private final SlobodnaMjesta slobodnaMjesta;
	private final BookingEventRepository events;
	private final ResourceRepository resources;
	private final UserRepository users;
	private final SlotService slotService;
	private final NotificationService notifications;
	private final ApplicationEventPublisher objave;
	private final Clock clock;
	private final Duration rokZaPotvrdu;
	private final Duration rokZaUplatu;
	private final Duration zatvaranjePoslije;
	private final int satiDoPodsjetnika;
	private final Counter napravljene;
	private final Counter sudari;

	public BookingService(BookingRepository bookings, WaitlistEntryRepository waitlist, SlobodnaMjesta slobodnaMjesta,
			BookingEventRepository events,
			ResourceRepository resources, UserRepository users, SlotService slotService,
			NotificationService notifications, ApplicationEventPublisher objave, Clock clock,
			@Value("${slotbook.booking.pending-hours}") int satiZaPotvrdu,
			@Value("${slotbook.booking.payment-minutes}") int minutaZaUplatu,
			@Value("${slotbook.booking.complete-after-hours}") int satiDoZatvaranja,
			@Value("${slotbook.booking.reminder-hours}") int satiDoPodsjetnika, MeterRegistry metrike) {
		this.bookings = bookings;
		this.waitlist = waitlist;
		this.slobodnaMjesta = slobodnaMjesta;
		this.events = events;
		this.resources = resources;
		this.users = users;
		this.slotService = slotService;
		this.notifications = notifications;
		this.objave = objave;
		this.clock = clock;
		this.rokZaPotvrdu = Duration.ofHours(satiZaPotvrdu);
		this.rokZaUplatu = Duration.ofMinutes(minutaZaUplatu);
		this.zatvaranjePoslije = Duration.ofHours(satiDoZatvaranja);
		this.satiDoPodsjetnika = satiDoPodsjetnika;
		this.napravljene = Counter.builder("slotbook.bookings.created")
				.description("Rezervacije upisane u bazu, i PENDING i CONFIRMED").register(metrike);
		this.sudari = Counter.builder("slotbook.bookings.conflicts")
				.description("Zahtjevi odbijeni jer je termin pun ili zauzet (409)").register(metrike);
	}

	// Ko smije rezervisati provjerava @PreAuthorize u kontroleru. Klijent šalje samo početak, a kraj
	// se uzima iz ponuđenog termina, pa rezervacija ne može biti duža ni pomjerena od termina u kalendaru.
	@Transactional
	public BookingResponse create(UUID resourceId, UUID clientId, Instant start, String idempotencyKey) {
		provjeriKljuc(idempotencyKey);
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
		// Mjesto bira Java pod lockom resursa, a EXCLUDE ne pusti dvije rezervacije na isto mjesto.
		// Prednost s liste čekanja čuva samo Java: ponuda nije rezervacija i ne ulazi u EXCLUDE.
		SlobodnaMjesta.Stanje stanje = slobodnaMjesta.za(resurs, clientId, termin, sada);
		if (stanje.mojeVec()) {
			throw new AlreadyInSlotException();
		}
		if (stanje.slobodna().isEmpty()) {
			sudari.increment();
			throw new SlotTakenException();
		}
		int mjesto = stanje.mjesto().orElseThrow(SlotOfferedException::new);
		Booking rezervacija = upisi(resurs, clientId, termin, mjesto, sada, idempotencyKey, null);
		objave.publishEvent(new SlotsChanged(resourceId));
		return BookingResponse.from(rezervacija);
	}

	// Isti termin svake sedmice, sve ili ništa. Svaka sedmica je svoja rezervacija, pa preklapanje i
	// dalje čuva EXCLUDE, a otkazuje se jedna po jedna. Provjera unaprijed je tu samo da 409 kaže koje
	// sedmice nisu slobodne: pod lockom resursa niko drugi ne upisuje, pa je tačna.
	@Transactional
	public BookingSeriesResponse createSeries(UUID resourceId, UUID clientId, Instant start, int sedmica,
			String idempotencyKey) {
		provjeriKljuc(idempotencyKey);
		Resource resurs = resources.findLockedById(resourceId).orElseThrow(ResourceNotFoundException::new);
		// Svaka sedmica bi trebala svoju uplatu u 15 minuta. Dok to ne riješimo, plaćen termin ide jedan po jedan.
		if (resurs.getPriceCents() > 0) {
			throw new InvalidFieldException("weeks",
					"Plaćen termin se za sada ne može rezervisati u seriji. Rezerviši sedmicu po sedmicu.");
		}
		Instant sada = clock.instant();
		javiIsteklim(bookings.expireOnResource(resourceId, sada));
		if (idempotencyKey != null) {
			var ranija = bookings.findByClientIdAndIdempotencyKey(clientId, idempotencyKey);
			if (ranija.isPresent()) {
				return ponovljenaSerija(ranija.get(), resourceId, start, sedmica);
			}
		}
		// Isti sat u zoni organizacije, ne isti razmak u UTC-u: termin u 09:00 ostaje u 09:00 i poslije
		// prelaska na ljetno vrijeme, a u UTC-u se pomjeri za sat.
		ZonedDateTime prva = start.atZone(ZoneId.of(resurs.getOrganization().getTimezone()));
		List<TimeRange> termini = new ArrayList<>();
		List<Integer> mjesta = new ArrayList<>();
		List<Instant> nisuSlobodni = new ArrayList<>();
		for (int i = 0; i < sedmica; i++) {
			Instant pocetak = prva.plusWeeks(i).toInstant();
			Optional<TimeRange> termin = slotService.offered(resourceId, pocetak);
			Optional<Integer> mjesto = termin.flatMap(t -> slobodnaMjesta.za(resurs, clientId, t, sada).mjesto());
			if (mjesto.isPresent()) {
				termini.add(termin.get());
				mjesta.add(mjesto.get());
			} else {
				nisuSlobodni.add(pocetak);
			}
		}
		if (!nisuSlobodni.isEmpty()) {
			throw new SeriesConflictException(nisuSlobodni);
		}
		UUID serija = UUID.randomUUID();
		List<BookingResponse> napravljeneSerije = new ArrayList<>();
		for (int i = 0; i < termini.size(); i++) {
			// Ključ nosi samo prva sedmica. UNIQUE (client_id, idempotency_key) bi odbio drugu.
			napravljeneSerije.add(BookingResponse.from(
					upisi(resurs, clientId, termini.get(i), mjesta.get(i), sada, i == 0 ? idempotencyKey : null,
							serija)));
		}
		objave.publishEvent(new SlotsChanged(resourceId));
		return new BookingSeriesResponse(serija, napravljeneSerije);
	}

	// Upis jedne rezervacije, i sam i u seriji, na mjesto koje je pozivalac izabrao pod lockom resursa.
	// Zauzetost na kraju čuva EXCLUDE.
	private Booking upisi(Resource resurs, UUID clientId, TimeRange termin, int mjesto, Instant sada,
			String idempotencyKey, UUID serija) {
		// Plaćen termin potvrđuje uplata, a ne provider, pa automatska potvrda tu ne važi.
		// Rok je kratak, jer neplaćena rezervacija drži termin koji bi neko drugi platio.
		boolean placa = resurs.getPriceCents() > 0;
		BookingStatus status = resurs.isAutoConfirm() && !placa ? BookingStatus.CONFIRMED : BookingStatus.PENDING;
		User klijent = users.getReferenceById(clientId);
		Booking nova = new Booking(resurs, klijent, termin.start(), termin.end(), status, sada, idempotencyKey,
				placa ? rokZaUplatu : rokZaPotvrdu);
		nova.uSeriju(serija);
		nova.naMjesto(mjesto);
		nova.poCijeni(resurs.getPriceCents());
		Booking rezervacija;
		try {
			rezervacija = bookings.saveAndFlush(nova);
		} catch (DataIntegrityViolationException e) {
			// Ostale greške integriteta nisu zauzet termin i ne smiju se tako prijaviti korisniku
			if (e.getMostSpecificCause() instanceof SQLException sql) {
				if (EXCLUSION_VIOLATION.equals(sql.getSQLState())) {
					sudari.increment();
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
		waitlist.markBooked(resurs.getId(), clientId, termin.start());
		obavijesti(rezervacija, clientId);
		napravljene.increment();
		return rezervacija;
	}

	private static void provjeriKljuc(String idempotencyKey) {
		if (idempotencyKey != null && (idempotencyKey.isBlank() || idempotencyKey.length() > NAJDUZI_KLJUC)) {
			throw new InvalidFieldException("Idempotency-Key", "Ključ mora imati od 1 do " + NAJDUZI_KLJUC
					+ " znakova. Najbolje je poslati novi UUID za svaku rezervaciju.");
		}
	}

	// Svaka promjena statusa ide ovuda. Pravilo prelaza čuva entitet, istovremenu izmjenu @Version,
	// a zapis u historiji je u istoj transakciji. Postojanje i pristup je provjerio @PreAuthorize,
	// a rezervacije se ne brišu.
	@Transactional
	public BookingResponse changeStatus(UUID bookingId, UUID actorId, BookingStatus novi) {
		Booking rezervacija = bookings.findById(bookingId).orElseThrow();
		// Ovuda potvrđuje osoblje. Uplata će potvrđivati svojim putem (webhook), pa provjera nije u entitetu.
		if (novi == BookingStatus.CONFIRMED && rezervacija.cekaUplatu()) {
			throw new AwaitingPaymentException();
		}
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
		List<UUID> istekle = bookings.expireAll(clock.instant());
		javiIsteklim(istekle);
		return istekle.size();
	}

	// Ne odmah na kraju termina: provider ima još dan da označi nedolazak, a COMPLETED je konačno
	@Transactional
	public int completeEnded() {
		Instant sada = clock.instant();
		return bookings.completeEndedBefore(sada.minus(zatvaranjePoslije), sada);
	}

	// Za BookingJob. Posao koji kasni (Render spava) i dalje pošalje podsjetnik, samo dok termin nije počeo.
	@Transactional
	public int sendReminders() {
		Instant sada = clock.instant();
		List<Booking> zaPodsjetnik = bookings.findDueForReminder(sada, sada.plus(Duration.ofHours(satiDoPodsjetnika)),
				satiDoPodsjetnika);
		for (Booking rezervacija : zaPodsjetnik) {
			javi(rezervacija.getClient().getId(), NotificationType.BOOKING_REMINDER, rezervacija);
		}
		return zaPodsjetnik.size();
	}

	// Najkasniji termin prvo, pa budući termini idu prije prošlih. Sort bira server, kao kod resursa,
	// a id razdvaja rezervacije s istim početkom (različiti resursi), da nijedna ne preskoči stranicu.
	@Transactional(readOnly = true)
	public PageResponse<MyBookingResponse> mine(UUID clientId, Pageable pageable) {
		Pageable poPocetku = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
				Sort.by(Sort.Order.desc("startTime"), Sort.Order.asc("id")));
		return PageResponse.from(bookings.findMine(clientId, poPocetku).map(MyBookingResponse::from));
	}

	// Raspored resursa za dane from..to u zoni organizacije, sve rezervacije bez obzira na stanje,
	// da provider vidi i otkazane. Period je ograničen kao kod slobodnih termina, pa nema stranica.
	@Transactional(readOnly = true)
	public List<ResourceBookingResponse> schedule(UUID resourceId, LocalDate from, LocalDate to) {
		SlotService.provjeri(from, to);
		Resource resurs = resources.findById(resourceId).orElseThrow(ResourceNotFoundException::new);
		ZoneId zona = ZoneId.of(resurs.getOrganization().getTimezone());
		return bookings.findSchedule(resourceId, from.atStartOfDay(zona).toInstant(), to.atStartOfDay(zona).toInstant())
				.stream()
				.map(ResourceBookingResponse::from)
				.toList();
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
		// Svaki prelaz, i potvrda: kalendaru vlasnika se mijenja oznaka "na čekanju" u "moje"
		objave.publishEvent(new SlotsChanged(rezervacija.getResource().getId()));
		return BookingResponse.from(rezervacija);
	}

	// Ko treba znati za novi status. U istoj transakciji kao promjena (outbox), a šalje se kasnije.
	// Dolazak, nedolazak i zatvaranje ne šalju ništa: klijent je tu bio ili nije, i to zna.
	private void obavijesti(Booking rezervacija, UUID actorId) {
		UUID klijent = rezervacija.getClient().getId();
		UUID provider = rezervacija.getResource().getProvider().getId();
		switch (rezervacija.getStatus()) {
			case PENDING -> {
				// Rezervaciju koja čeka uplatu provider ne potvrđuje, pa nema šta ni javiti
				if (!rezervacija.cekaUplatu()) {
					javi(provider, NotificationType.BOOKING_AWAITS_CONFIRMATION, rezervacija);
				}
			}
			case CONFIRMED -> javi(klijent, NotificationType.BOOKING_CONFIRMED, rezervacija);
			case REJECTED -> javi(klijent, NotificationType.BOOKING_REJECTED, rezervacija);
			// Javlja se drugoj strani: kad otkaže klijent, provideru, a kad otkaže osoblje, klijentu
			case CANCELLED -> javi(actorId.equals(klijent) ? provider : klijent, NotificationType.BOOKING_CANCELLED,
					rezervacija);
			default -> {
			}
		}
	}

	private void javi(UUID primalac, NotificationType tip, Booking rezervacija) {
		notifications.enqueueForBooking(primalac, tip, rezervacija.getId(), BookingEmails.za(tip, rezervacija, clock.instant()));
	}

	// Za tekst emaila trebaju naziv resursa i zona, pa se istekle učitaju. Po krugu posla ih je malo.
	private void javiIsteklim(List<UUID> istekle) {
		for (Booking rezervacija : bookings.findAllById(istekle)) {
			javi(rezervacija.getClient().getId(), NotificationType.BOOKING_EXPIRED, rezervacija);
			objave.publishEvent(new SlotsChanged(rezervacija.getResource().getId()));
		}
	}

	// Isti ključ i isti zahtjev vraćaju raniju rezervaciju, u stanju u kojem je sada. Isti ključ
	// za drugi termin je greška klijenta (422 po nacrtu IETF-a za Idempotency-Key).
	private BookingSeriesResponse ponovljenaSerija(Booking ranija, UUID resourceId, Instant start, int sedmica) {
		List<Booking> serija = ranija.getSeriesId() == null ? List.of()
				: bookings.findBySeriesIdOrderByStartTime(ranija.getSeriesId());
		if (serija.size() != sedmica || !ranija.getResource().getId().equals(resourceId)
				|| !ranija.getStartTime().equals(start)) {
			throw new IdempotencyKeyReusedException();
		}
		return new BookingSeriesResponse(ranija.getSeriesId(), serija.stream().map(BookingResponse::from).toList());
	}

	private BookingResponse ponovljen(Booking ranija, UUID resourceId, Instant start) {
		if (!ranija.getResource().getId().equals(resourceId) || !ranija.getStartTime().equals(start)) {
			throw new IdempotencyKeyReusedException();
		}
		return BookingResponse.from(ranija);
	}

}
