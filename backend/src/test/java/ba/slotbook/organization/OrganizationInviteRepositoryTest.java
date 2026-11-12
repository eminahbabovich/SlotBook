package ba.slotbook.organization;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import ba.slotbook.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@DataJpaTest
class OrganizationInviteRepositoryTest {

	private static final Instant SADA = Instant.parse("2026-11-12T10:00:00Z");
	private static final Instant ZA_SEDMICU = SADA.plus(Duration.ofDays(7));

	@Autowired
	OrganizationInviteRepository invites;

	@Autowired
	OrganizationRepository organizations;

	@Autowired
	JdbcTemplate jdbc;

	private Organization citaonica;

	@BeforeEach
	void organizacijaPostoji() {
		citaonica = organizations.saveAndFlush(new Organization("Čitaonica FIT", "citaonica-fit", "Europe/Sarajevo", SADA));
	}

	private OrganizationInvite pozivnica(Organization organizacija, String email, String hash) {
		return invites.saveAndFlush(
				new OrganizationInvite(organizacija, email, OrganizationRole.CLIENT, hash, SADA, ZA_SEDMICU));
	}

	@Test
	void bazaNePustaDvijeOtvorenePozivniceZaIstiEmail() {
		pozivnica(citaonica, "hana@example.com", "a".repeat(64));

		assertThatThrownBy(() -> pozivnica(citaonica, "hana@example.com", "b".repeat(64)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("organization_invites_jedna_otvorena");
	}

	@Test
	void prihvacenaPozivnicaNeSmetaNovoj() {
		OrganizationInvite stara = pozivnica(citaonica, "hana@example.com", "a".repeat(64));
		jdbc.update("update organization_invites set accepted_at = now() where id = ?", stara.getId());

		assertThatCode(() -> pozivnica(citaonica, "hana@example.com", "b".repeat(64))).doesNotThrowAnyException();
	}

	@Test
	void istiEmailMozeImatiOtvorenePozivniceURazlicitimOrganizacijama() {
		Organization teretana = organizations.saveAndFlush(
				new Organization("Teretana Grbavica", "teretana-grbavica", "Europe/Sarajevo", SADA));
		pozivnica(citaonica, "hana@example.com", "a".repeat(64));

		assertThatCode(() -> pozivnica(teretana, "hana@example.com", "b".repeat(64))).doesNotThrowAnyException();
	}

	@Test
	void bazaOdbijaEmailSVelikimSlovima() {
		assertThatThrownBy(() -> jdbc.update("""
				insert into organization_invites (id, organization_id, email, role, token_hash, created_at, expires_at)
				values (gen_random_uuid(), ?, 'Hana@example.com', 'CLIENT', ?, now(), now() + interval '7 days')""",
				citaonica.getId(), "c".repeat(64)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("organization_invites_email_mala_slova");
	}

	@Test
	void bazaOdbijaRokPrijeSlanja() {
		assertThatThrownBy(() -> jdbc.update("""
				insert into organization_invites (id, organization_id, email, role, token_hash, created_at, expires_at)
				values (gen_random_uuid(), ?, 'hana@example.com', 'CLIENT', ?, now(), now() - interval '1 day')""",
				citaonica.getId(), "d".repeat(64)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("organization_invites_rok_poslije_slanja");
	}

}
