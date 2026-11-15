package ba.slotbook.availability;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Slobodni termini se računaju iz pravila, a ne čitaju iz tabele termina. Bez Springa i bez baze,
// pa se sve, i prelazak na ljetno vrijeme, provjerava običnim unit testom.
public record SlotCalculator(ZoneId zone, List<AvailabilityInterval> rules, Duration slotLength,
		List<TimeRange> timeOff) {

	// Termini za datume od from do toExclusive (bez njega), koji počinju poslije trenutka now
	public List<TimeRange> between(LocalDate from, LocalDate toExclusive, Instant now) {
		return allBetween(from, toExclusive).stream()
				.filter(termin -> termin.start().isAfter(now))
				.toList();
	}

	// Svi termini perioda, i oni koji su već prošli. Kalendaru trebaju samo budući, a statistici svi.
	public List<TimeRange> allBetween(LocalDate from, LocalDate toExclusive) {
		List<TimeRange> termini = new ArrayList<>();
		for (LocalDate dan : from.datesUntil(toExclusive).toList()) {
			for (AvailabilityInterval pravilo : rules) {
				if (pravilo.dayOfWeek() == dan.getDayOfWeek()) {
					termini.addAll(terminiPravila(dan, pravilo));
				}
			}
		}
		termini.sort(Comparator.comparing(TimeRange::start));
		return termini;
	}

	// Imenilac popunjenosti: koliko se minuta moglo rezervisati. Broje se termini, ne radno vrijeme,
	// pa 08:00-10:30 sa satnim terminima daje 120 minuta, jer zadnjih pola sata niko ne može uzeti.
	public long offeredMinutes(LocalDate from, LocalDate toExclusive) {
		return allBetween(from, toExclusive).stream()
				.mapToLong(termin -> Duration.between(termin.start(), termin.end()).toMinutes())
				.sum();
	}

	// Lokalno 08:00 se pretvara u trenutak za baš taj datum, s pomakom koji tada važi u zoni
	// (+01:00 zimi, +02:00 ljeti). Zato pravilo "ponedjeljkom u 08:00" ostaje 08:00 po satu
	// na zidu i poslije prelaska na ljetno vrijeme. Termini se onda ređaju po stvarnim minutama
	// (Instant + trajanje), pa termin od sat vremena traje sat vremena i u noći kad se sat pomjera.
	private List<TimeRange> terminiPravila(LocalDate dan, AvailabilityInterval pravilo) {
		Instant kraj = ZonedDateTime.of(dan, pravilo.end(), zone).toInstant();
		Instant pocetak = ZonedDateTime.of(dan, pravilo.start(), zone).toInstant();

		List<TimeRange> termini = new ArrayList<>();
		// Zadnji termin mora cijeli stati u radno vrijeme: 08:00-10:30 sa satnim terminima daje 08 i 09
		while (!pocetak.plus(slotLength).isAfter(kraj)) {
			TimeRange termin = new TimeRange(pocetak, pocetak.plus(slotLength));
			if (timeOff.stream().noneMatch(termin::overlaps)) {
				termini.add(termin);
			}
			pocetak = termin.end();
		}
		return termini;
	}

}
