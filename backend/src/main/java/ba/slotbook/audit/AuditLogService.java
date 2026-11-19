package ba.slotbook.audit;

import java.time.Clock;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.paging.PageResponse;
import ba.slotbook.user.UserRepository;

@Service
public class AuditLogService {

	private final AuditEntryRepository entries;
	private final UserRepository users;
	private final Clock clock;

	public AuditLogService(AuditEntryRepository entries, UserRepository users, Clock clock) {
		this.entries = entries;
		this.users = users;
		this.clock = clock;
	}

	// MANDATORY: zapis nastaje samo unutar transakcije radnje. Ako radnja padne, nestane i zapis,
	// a zapis o radnji koja se nije desila ne može ostati.
	@Transactional(propagation = Propagation.MANDATORY)
	public void record(UUID organizationId, UUID actorId, AuditAction action, UUID targetId, String detail) {
		entries.save(new AuditEntry(organizationId, users.getReferenceById(actorId), action, targetId, detail,
				clock.instant()));
	}

	@Transactional(readOnly = true)
	public PageResponse<AuditEntryResponse> list(UUID organizationId, Pageable pageable) {
		Pageable najnovijePrvo = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
				Sort.by(Sort.Direction.DESC, "id"));
		return PageResponse.from(entries.findForOrganization(organizationId, najnovijePrvo)
				.map(AuditEntryResponse::from));
	}

}
