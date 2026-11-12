package ba.slotbook.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class OrganizationInviteTest {

	@Test
	void pozivnicaJeIsteklaUTrenutkuIsteka() {
		Instant istice = Instant.parse("2026-11-19T10:00:00Z");
		OrganizationInvite pozivnica = new OrganizationInvite(new Organization("Čitaonica FIT", "citaonica-fit",
				"Europe/Sarajevo", istice.minusSeconds(60)), "hana@example.com", OrganizationRole.CLIENT, "hash",
				istice.minusSeconds(60), istice);

		assertThat(pozivnica.isExpired(istice.minusSeconds(1))).isFalse();
		assertThat(pozivnica.isExpired(istice)).isTrue();
	}

}
