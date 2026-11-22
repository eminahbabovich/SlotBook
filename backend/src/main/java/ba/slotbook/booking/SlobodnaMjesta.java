package ba.slotbook.booking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;

import ba.slotbook.availability.TimeRange;
import ba.slotbook.resource.Resource;

// Koliko mjesta u terminu može dobiti jedan klijent. Isto pravilo treba rezervaciji, seriji, listi
// čekanja i poslu koji nudi oslobođena mjesta, pa je na jednom mjestu. Tačno je samo pod lockom resursa.
@Component
class SlobodnaMjesta {

	private final BookingRepository bookings;
	private final WaitlistEntryRepository waitlist;

	SlobodnaMjesta(BookingRepository bookings, WaitlistEntryRepository waitlist) {
		this.bookings = bookings;
		this.waitlist = waitlist;
	}

	// mojeVec: klijent već drži mjesto. slobodna: mjesta koja nijedna rezervacija ne drži.
	// ponudeDrugima: ponude s liste čekanja koje još traju, a nisu ovom klijentu. Svaka čuva jedno mjesto.
	record Stanje(boolean mojeVec, List<Integer> slobodna, long ponudeDrugima) {

		// Mjesto koje klijent dobija, ako ga ima: prvo slobodno, kad ostane i poslije tuđih ponuda
		Optional<Integer> mjesto() {
			return !mojeVec && slobodna.size() > ponudeDrugima ? Optional.of(slobodna.getFirst()) : Optional.empty();
		}

	}

	Stanje za(Resource resurs, UUID clientId, TimeRange termin, Instant sada) {
		List<Booking> drze = bookings.findByResourceIdAndStatusInAndStartTimeBeforeAndEndTimeAfter(resurs.getId(),
				BookingStatus.DRZE_TERMIN, termin.end(), termin.start());
		return new Stanje(drze.stream().anyMatch(b -> b.getClient().getId().equals(clientId)),
				slobodna(drze, resurs.getCapacity()),
				waitlist.countOffersToOthers(resurs.getId(), termin.start(), termin.end(), clientId, sada));
	}

	// Mjesta od 1 do kapaciteta koja nijedna od datih rezervacija ne drži, od najnižeg
	static List<Integer> slobodna(List<Booking> drze, int kapacitet) {
		Set<Integer> zauzeta = drze.stream().map(Booking::getSeat).collect(Collectors.toSet());
		return IntStream.rangeClosed(1, kapacitet).boxed().filter(m -> !zauzeta.contains(m)).toList();
	}

}
