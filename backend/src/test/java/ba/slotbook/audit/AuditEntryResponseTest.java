package ba.slotbook.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class AuditEntryResponseTest {

	// Brisanje naloga postavi actor na null, a zapis o radnji ostaje
	@Test
	void zapisObrisanogNalogaOstajeBezImenaIId() {
		UUID sala = UUID.randomUUID();
		AuditEntry zapis = new AuditEntry(null, null, AuditAction.RESOURCE_DEACTIVATED, sala, "Sala 2",
				Instant.parse("2026-11-19T17:00:00Z"));

		AuditEntryResponse odgovor = AuditEntryResponse.from(zapis);

		assertThat(odgovor.actorId()).isNull();
		assertThat(odgovor.actorName()).isNull();
		assertThat(odgovor.action()).isEqualTo(AuditAction.RESOURCE_DEACTIVATED);
		assertThat(odgovor.targetId()).isEqualTo(sala);
		assertThat(odgovor.detail()).isEqualTo("Sala 2");
		assertThat(odgovor.createdAt()).isEqualTo(Instant.parse("2026-11-19T17:00:00Z"));
	}

}
