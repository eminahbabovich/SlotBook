package ba.slotbook.booking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.availability.TimeRange;
import ba.slotbook.booking.BookingRuleException.Pravilo;
import ba.slotbook.organization.MyOrganizationResponse;
import ba.slotbook.organization.NoShowRules;
import ba.slotbook.organization.OrganizationRole;
import ba.slotbook.organization.OrganizationService;
import ba.slotbook.resource.Resource;
import ba.slotbook.resource.ResourceRules;

// Pravila korištenja: blokada zbog nedolazaka, najviše budućih rezervacija, koliko dana unaprijed i
// najmanje minuta prije početka. Važe za klijente. Admin i provider rezervišu i za druge (npr. termin
// za studenta koji je zvao telefonom), pa ih pravila ne ograničavaju.
// Poziva se pod lockom resursa, pa dva istovremena zahtjeva istog člana ne prođu oba brojanje.
@Component
class BookingRules {

	private static final List<BookingStatus> AKTIVNE = List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED);
	private static final DateTimeFormatter DAN = DateTimeFormatter.ofPattern("EEEE, d. MMMM",
			Locale.forLanguageTag("bs"));
	private static final DateTimeFormatter DAN_I_SAT = DateTimeFormatter.ofPattern("EEEE, d. MMMM 'u' HH:mm",
			Locale.forLanguageTag("bs"));

	private final BookingRepository bookings;
	private final OrganizationService organizations;
	private final Clock clock;

	BookingRules(BookingRepository bookings, OrganizationService organizations, Clock clock) {
		this.bookings = bookings;
		this.organizations = organizations;
		this.clock = clock;
	}

	// termini su poredani po vremenu: jedan za običnu rezervaciju, više za seriju (sve ili ništa)
	void provjeri(Resource resurs, UUID clientId, List<TimeRange> termini, Instant sada) {
		UUID organizacija = resurs.getOrganization().getId();
		boolean vodiTermine = organizations.findRole(organizacija, clientId)
				.map(uloga -> uloga == OrganizationRole.ADMIN || uloga == OrganizationRole.PROVIDER)
				.orElse(false);
		if (vodiTermine) {
			return;
		}
		ZoneId zona = ZoneId.of(resurs.getOrganization().getTimezone());
		Optional<Instant> blokada = blokiranDo(organizacija, resurs.getOrganization().getNedolasci(), clientId, sada);
		if (blokada.isPresent()) {
			throw new BookingRuleException(Pravilo.BLOCKED, "Zbog nedolazaka ne možeš rezervisati u ovoj organizaciji do "
					+ DAN_I_SAT.format(blokada.get().atZone(zona)) + ". Ako je greška, javi se adminu.", blokada.get());
		}
		ResourceRules pravila = resurs.getPravila();
		Instant prvi = termini.getFirst().start();
		if (pravila.minNoticeMinutes() != null
				&& prvi.isBefore(sada.plus(Duration.ofMinutes(pravila.minNoticeMinutes())))) {
			throw new BookingRuleException(Pravilo.TOO_SOON, "Ovaj resurs se rezerviše najkasnije "
					+ trajanje(pravila.minNoticeMinutes()) + " prije početka. Izaberi kasniji termin.", null);
		}
		if (pravila.maxDaysAhead() != null) {
			LocalDate zadnjiDan = LocalDate.ofInstant(sada, zona).plusDays(pravila.maxDaysAhead());
			if (LocalDate.ofInstant(termini.getLast().start(), zona).isAfter(zadnjiDan)) {
				throw new BookingRuleException(Pravilo.TOO_FAR_AHEAD, "Ovaj resurs se rezerviše najviše "
						+ pravila.maxDaysAhead() + " " + (pravila.maxDaysAhead() == 1 ? "dan" : "dana")
						+ " unaprijed, zadnji dan je " + DAN.format(zadnjiDan) + ".", null);
			}
		}
		if (pravila.maxActivePerMember() != null) {
			long vec = bookings.countFutureOnResource(resurs.getId(), clientId, AKTIVNE, sada);
			if (vec + termini.size() > pravila.maxActivePerMember()) {
				throw new BookingRuleException(Pravilo.MAX_ACTIVE, "Granica budućih rezervacija po članu na ovom "
						+ "resursu je " + pravila.maxActivePerMember() + ", a ti već imaš " + vec
						+ (termini.size() > 1 ? ", pa serija od " + termini.size() + " sedmica ne stane" : "")
						+ ". Otkaži neku ili sačekaj da prođe.", null);
			}
		}
	}

	// Za traku u Mojim rezervacijama: samo organizacije u kojima je član sada blokiran
	@Transactional(readOnly = true)
	public List<BlockResponse> blokade(UUID userId) {
		Instant sada = clock.instant();
		List<BlockResponse> blokade = new ArrayList<>();
		for (MyOrganizationResponse org : organizations.listForUser(userId)) {
			if (org.role() != OrganizationRole.CLIENT || !org.noShowRules().postoji()) {
				continue;
			}
			Nedolasci nedolasci = nedolasci(org.id(), org.noShowRules(), userId, sada);
			blokiranDo(nedolasci, org.noShowRules(), sada).ifPresent(do_ -> blokade
					.add(new BlockResponse(org.id(), org.name(), org.timezone(), do_, nedolasci.broj())));
		}
		return blokade;
	}

	// Ne čuva se nigdje: limit nedolazaka u zadnjih windowDays dana blokira do zadnjeg nedolaska plus
	// blockDays. Kad stari nedolasci izađu iz perioda ili blokada istekne, član ponovo rezerviše sam.
	private Optional<Instant> blokiranDo(UUID organizacija, NoShowRules pravila, UUID clientId, Instant sada) {
		if (!pravila.postoji()) {
			return Optional.empty();
		}
		return blokiranDo(nedolasci(organizacija, pravila, clientId, sada), pravila, sada);
	}

	private Nedolasci nedolasci(UUID organizacija, NoShowRules pravila, UUID clientId, Instant sada) {
		return bookings.noShows(organizacija, clientId, sada.minus(Duration.ofDays(pravila.windowDays())), sada);
	}

	private static Optional<Instant> blokiranDo(Nedolasci nedolasci, NoShowRules pravila, Instant sada) {
		if (nedolasci.broj() < pravila.limit()) {
			return Optional.empty();
		}
		Instant kraj = nedolasci.zadnji().plus(Duration.ofDays(pravila.blockDays()));
		return kraj.isAfter(sada) ? Optional.of(kraj) : Optional.empty();
	}

	private static String trajanje(int minuta) {
		if (minuta % 60 == 0) {
			int sati = minuta / 60;
			return sati == 1 ? "sat" : sati + (sati % 10 >= 2 && sati % 10 <= 4 && (sati < 10 || sati > 20) ? " sata" : " sati");
		}
		return minuta + " minuta";
	}

}
