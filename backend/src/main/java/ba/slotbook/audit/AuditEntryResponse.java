package ba.slotbook.audit;

import java.time.Instant;
import java.util.UUID;

// actorId i actorName su null kad je nalog onoga ko je radnju uradio obrisan
public record AuditEntryResponse(AuditAction action, UUID actorId, String actorName, UUID targetId, String detail,
		Instant createdAt) {

	static AuditEntryResponse from(AuditEntry zapis) {
		var ko = zapis.getActor();
		return new AuditEntryResponse(zapis.getAction(), ko == null ? null : ko.getId(),
				ko == null ? null : ko.getDisplayName(), zapis.getTargetId(), zapis.getDetail(), zapis.getCreatedAt());
	}

}
