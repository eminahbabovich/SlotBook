package ba.slotbook.availability;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.error.InvalidFieldException;
import ba.slotbook.paging.PageResponse;
import ba.slotbook.resource.ResourceRepository;

@Service
public class TimeOffService {

	private final TimeOffRepository timeOffs;
	private final ResourceRepository resources;
	private final Clock clock;

	public TimeOffService(TimeOffRepository timeOffs, ResourceRepository resources, Clock clock) {
		this.timeOffs = timeOffs;
		this.resources = resources;
		this.clock = clock;
	}

	// Resurs postoji, to je provjerio @PreAuthorize, pa je dovoljna referenca bez SELECT-a
	@Transactional
	public TimeOffResponse create(UUID resourceId, OffsetDateTime startsAt, OffsetDateTime endsAt, String reason) {
		provjeri(startsAt, endsAt);
		TimeOff timeOff = timeOffs.save(new TimeOff(resources.getReferenceById(resourceId), startsAt.toInstant(),
				endsAt.toInstant(), reason, clock.instant()));
		return TimeOffResponse.from(timeOff);
	}

	// Porede se trenuci, a ne tekst: 10:15+02:00 je prije 10:00+01:00
	static void provjeri(OffsetDateTime startsAt, OffsetDateTime endsAt) {
		if (!startsAt.toInstant().isBefore(endsAt.toInstant())) {
			throw new InvalidFieldException("endsAt", "Kraj izuzetka mora biti poslije početka.");
		}
	}

	// Samo izuzeci koji još traju ili tek dolaze. Prošli ne utiču na termine, a lista bi s
	// godinama samo rasla.
	@Transactional(readOnly = true)
	public PageResponse<TimeOffResponse> listCurrent(UUID resourceId, Pageable pageable) {
		Pageable poPocetku = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
				Sort.by("startsAt", "id"));
		return PageResponse.from(timeOffs.findByResourceIdAndEndsAtAfter(resourceId, clock.instant(), poPocetku)
				.map(TimeOffResponse::from));
	}

	// Traži se po paru (izuzetak, resurs iz putanje), isto kao resurs po organizaciji
	@Transactional
	public void delete(UUID resourceId, UUID timeOffId) {
		TimeOff timeOff = timeOffs.findByIdAndResourceId(timeOffId, resourceId)
				.orElseThrow(TimeOffNotFoundException::new);
		timeOffs.delete(timeOff);
	}

}
