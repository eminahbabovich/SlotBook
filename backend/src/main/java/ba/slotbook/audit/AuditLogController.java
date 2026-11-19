package ba.slotbook.audit;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import ba.slotbook.paging.PageResponse;

@RestController
public class AuditLogController {

	private final AuditLogService auditLog;

	public AuditLogController(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	// Samo admin, i samo za organizaciju iz putanje: upit ne vidi zapise drugih organizacija
	@GetMapping("/api/organizations/{id}/audit-log")
	@PreAuthorize("@organizationAccess.hasRole(#id, authentication, 'ADMIN')")
	public PageResponse<AuditEntryResponse> list(@PathVariable UUID id, Pageable pageable) {
		return auditLog.list(id, pageable);
	}

}
