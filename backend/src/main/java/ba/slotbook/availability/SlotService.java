package ba.slotbook.availability;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.error.InvalidFieldException;
import ba.slotbook.resource.Resource;
import ba.slotbook.resource.ResourceNotFoundException;
import ba.slotbook.resource.ResourceRepository;

@Service
public class SlotService {

	// Mjesec dana je dovoljan za kalendar, a ograničava koliko termina jedan zahtjev može tražiti
	static final int NAJVISE_DANA = 31;

	private final ResourceRepository resources;
	private final AvailabilityRuleRepository rules;
	private final TimeOffRepository timeOffs;
	private final Clock clock;

	public SlotService(ResourceRepository resources, AvailabilityRuleRepository rules, TimeOffRepository timeOffs,
			Clock clock) {
		this.resources = resources;
		this.rules = rules;
		this.timeOffs = timeOffs;
		this.clock = clock;
	}

	// from i to su datumi u zoni organizacije, a to se ne uključuje: from=16.11. i to=17.11. je jedan dan
	@Transactional(readOnly = true)
	public SlotsResponse slots(UUID resourceId, LocalDate from, LocalDate to) {
		provjeri(from, to);
		Resource resurs = resources.findById(resourceId).orElseThrow(ResourceNotFoundException::new);
		ZoneId zona = ZoneId.of(resurs.getOrganization().getTimezone());
		// Admin i provider vide i deaktiviran resurs, ali na njemu se ne može rezervisati
		if (!resurs.isActive()) {
			return new SlotsResponse(zona.getId(), resurs.getCapacity(), List.of());
		}
		return new SlotsResponse(zona.getId(), resurs.getCapacity(),
				kalkulator(resurs, from, to).between(from, to, clock.instant()));
	}

	// Svi termini jednog dana, i oni koji su već počeli. Za ekran na vratima, koji pokazuje i termin u toku.
	@Transactional(readOnly = true)
	public SlotsResponse wholeDay(UUID resourceId, LocalDate dan) {
		Resource resurs = resources.findById(resourceId).orElseThrow(ResourceNotFoundException::new);
		ZoneId zona = ZoneId.of(resurs.getOrganization().getTimezone());
		if (!resurs.isActive()) {
			return new SlotsResponse(zona.getId(), resurs.getCapacity(), List.of());
		}
		return new SlotsResponse(zona.getId(), resurs.getCapacity(),
				kalkulator(resurs, dan, dan.plusDays(1)).allBetween(dan, dan.plusDays(1)));
	}

	// Za popunjenost u statistici. Bez ograničenja od 31 dan, jer period ograničava statistika, i bez
	// provjere aktivnosti: resurs deaktiviran danas je prošlog mjeseca možda radio, a historiju
	// aktivacije ni radnog vremena ne čuvamo, pa se računa po pravilima kakva su sada.
	@Transactional(readOnly = true)
	public long offeredMinutes(UUID resourceId, LocalDate from, LocalDate to) {
		Resource resurs = resources.findById(resourceId).orElseThrow(ResourceNotFoundException::new);
		// Minute mjesta: sala s 5 mjesta ponudi 5 sati u jednom satu, pa puna sala ima 100 %, a ne 500 %
		return kalkulator(resurs, from, to).offeredMinutes(from, to) * resurs.getCapacity();
	}

	private SlotCalculator kalkulator(Resource resurs, LocalDate from, LocalDate to) {
		ZoneId zona = ZoneId.of(resurs.getOrganization().getTimezone());
		Instant pocetak = from.atStartOfDay(zona).toInstant();
		Instant kraj = to.atStartOfDay(zona).toInstant();
		List<TimeRange> izuzeci = timeOffs.findByResourceIdAndStartsAtBeforeAndEndsAtAfter(resurs.getId(), kraj,
				pocetak)
				.stream()
				.map(t -> new TimeRange(t.getStartsAt(), t.getEndsAt()))
				.toList();
		List<AvailabilityInterval> pravila = rules.findByResourceId(resurs.getId()).stream()
				.map(AvailabilityRule::interval)
				.toList();
		return new SlotCalculator(zona, pravila, Duration.ofMinutes(resurs.getSlotMinutes()), izuzeci);
	}

	// Termin s ovim početkom, ako ga kalendar nudi: poravnat na trajanje, u radnom vremenu, izvan
	// izuzetaka i u budućnosti. Zauzetost se ovdje namjerno ne gleda. Nju čuva baza (EXCLUDE), jer
	// bi provjera u Javi kod dva istovremena zahtjeva u oba vidjela slobodan termin.
	@Transactional(readOnly = true)
	public Optional<TimeRange> offered(UUID resourceId, Instant start) {
		Resource resurs = resources.findById(resourceId).orElseThrow(ResourceNotFoundException::new);
		LocalDate dan = start.atZone(ZoneId.of(resurs.getOrganization().getTimezone())).toLocalDate();
		return slots(resourceId, dan, dan.plusDays(1)).slots().stream()
				.filter(termin -> termin.start().equals(start))
				.findFirst();
	}

	// Javno, jer isti period (do 31 dan, to se ne uključuje) prima i raspored rezervacija
	public static void provjeri(LocalDate from, LocalDate to) {
		if (!from.isBefore(to)) {
			throw new InvalidFieldException("to",
					"Datum do mora biti poslije datuma od. Za jedan dan pošalji npr. from=2026-11-16 i to=2026-11-17.");
		}
		if (ChronoUnit.DAYS.between(from, to) > NAJVISE_DANA) {
			throw new InvalidFieldException("to",
					"Period može biti najviše " + NAJVISE_DANA + " dan. Za duži period pošalji više zahtjeva.");
		}
	}

}
