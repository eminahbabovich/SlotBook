package ba.slotbook.account;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ba.slotbook.audit.AuditAction;
import ba.slotbook.audit.AuditLogService;

// Odvojeno od AccountDeletionService, jer provjera lozinke mora biti van transakcije (vidi tamo), a
// @Transactional na metodi iste klase pozvanoj iznutra ne bi prošao kroz Spring proxy.
@Service
class AccountAnonymization {

	private final AccountDeletionRepository data;
	private final AuditLogService auditLog;
	private final Clock clock;

	AccountAnonymization(AccountDeletionRepository data, AuditLogService auditLog, Clock clock) {
		this.data = data;
		this.auditLog = auditLog;
		this.clock = clock;
	}

	// Sve ili ništa: ako bilo šta padne, nalog ostaje kakav je bio
	@Transactional
	public void anonymize(UUID userId, String email) {
		Instant sada = clock.instant();
		data.lockAdminOrganizations(userId);
		List<String> bezAdmina = data.organizationsWithOnlyThisAdmin(userId);
		List<String> resursi = data.ledResources(userId);
		if (!bezAdmina.isEmpty() || !resursi.isEmpty()) {
			throw new AccountDeletionBlockedException(bezAdmina, resursi);
		}

		data.cancelFutureBookings(userId, sada);
		for (AccountDeletionRepository.Membership clanstvo : data.memberships(userId)) {
			auditLog.record(clanstvo.organizationId(), userId, AuditAction.ACCOUNT_DELETED, userId, clanstvo.role());
		}
		data.revokeSessions(userId, sada);
		data.deletePersonalRows(userId, email);
		data.anonymize(userId, sada);
	}

}
