package ba.slotbook.availability;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.error.InvalidFieldException;
import ba.slotbook.resource.Resource;
import ba.slotbook.resource.ResourceNotFoundException;
import ba.slotbook.resource.ResourceRepository;

@Service
public class AvailabilityService {

	private final AvailabilityRuleRepository rules;
	private final ResourceRepository resources;

	public AvailabilityService(AvailabilityRuleRepository rules, ResourceRepository resources) {
		this.rules = rules;
		this.resources = resources;
	}

	@Transactional(readOnly = true)
	public AvailabilityResponse get(UUID resourceId) {
		Resource resurs = resources.findById(resourceId).orElseThrow(ResourceNotFoundException::new);
		return odgovor(resurs, rules.findByResourceId(resourceId));
	}

	// Cijela sedmica se mijenja odjednom. Red resursa se zaključa (SELECT ... FOR UPDATE), pa dva
	// istovremena PUT-a idu jedan za drugim i ostane sedmica iz jednog, a ne mješavina oba.
	@Transactional
	public AvailabilityResponse replace(UUID resourceId, List<AvailabilityInterval> intervali) {
		provjeri(intervali);
		Resource resurs = resources.findLockedById(resourceId).orElseThrow(ResourceNotFoundException::new);
		rules.deleteAllOfResource(resourceId);
		List<AvailabilityRule> nova = rules.saveAll(intervali.stream()
				.map(i -> new AvailabilityRule(resurs, i.dayOfWeek(), i.start(), i.end()))
				.toList());
		return odgovor(resurs, nova);
	}

	// Isto čuvaju CHECK i EXCLUDE u bazi, ali odatle bi greška stigla kao 500 bez imena polja.
	// Najviše 50 intervala, pa poređenje svakog sa svakim ne košta ništa, a lako se objasni.
	static void provjeri(List<AvailabilityInterval> intervali) {
		for (int i = 0; i < intervali.size(); i++) {
			AvailabilityInterval interval = intervali.get(i);
			if (!interval.start().isBefore(interval.end())) {
				throw new InvalidFieldException("rules[" + i + "].end",
						"Kraj mora biti poslije početka. Rad preko ponoći upiši kao dva intervala, u dva dana.");
			}
		}
		for (int i = 0; i < intervali.size(); i++) {
			for (int j = i + 1; j < intervali.size(); j++) {
				AvailabilityInterval a = intervali.get(i);
				AvailabilityInterval b = intervali.get(j);
				// Kraj je isključen: 08:00-12:00 i 12:00-16:00 se dodiruju, ali ne preklapaju
				if (a.dayOfWeek() == b.dayOfWeek() && a.start().isBefore(b.end()) && b.start().isBefore(a.end())) {
					// Polje kaže koji interval, a poruka je za korisnika, pa u njoj nema rules[i]
					throw new InvalidFieldException("rules[" + j + "]",
							"Ovaj interval se preklapa s drugim intervalom istog dana.");
				}
			}
		}
	}

	private AvailabilityResponse odgovor(Resource resurs, List<AvailabilityRule> pravila) {
		List<AvailabilityInterval> intervali = pravila.stream()
				.map(AvailabilityRule::interval)
				.sorted(Comparator.comparing(AvailabilityInterval::dayOfWeek)
						.thenComparing(AvailabilityInterval::start))
				.toList();
		return new AvailabilityResponse(resurs.getOrganization().getTimezone(), intervali);
	}

}
