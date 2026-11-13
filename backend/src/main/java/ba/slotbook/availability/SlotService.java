package ba.slotbook.availability;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
			return new SlotsResponse(zona.getId(), List.of());
		}

		Instant pocetak = from.atStartOfDay(zona).toInstant();
		Instant kraj = to.atStartOfDay(zona).toInstant();
		List<TimeRange> izuzeci = timeOffs.findByResourceIdAndStartsAtBeforeAndEndsAtAfter(resourceId, kraj, pocetak)
				.stream()
				.map(t -> new TimeRange(t.getStartsAt(), t.getEndsAt()))
				.toList();
		List<AvailabilityInterval> pravila = rules.findByResourceId(resourceId).stream()
				.map(AvailabilityRule::interval)
				.toList();

		var kalkulator = new SlotCalculator(zona, pravila, Duration.ofMinutes(resurs.getSlotMinutes()), izuzeci);
		return new SlotsResponse(zona.getId(), kalkulator.between(from, to, clock.instant()));
	}

	static void provjeri(LocalDate from, LocalDate to) {
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
