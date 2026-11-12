package ba.slotbook.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.user.User;
import ba.slotbook.user.UserRepository;

@Import(TestcontainersConfiguration.class)
@DataJpaTest
class OrganizationMemberRepositoryTest {

	private static final Instant SADA = Instant.parse("2026-11-12T10:00:00Z");

	@Autowired
	OrganizationRepository organizations;

	@Autowired
	OrganizationMemberRepository members;

	@Autowired
	UserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	private Organization organizacija(String slug) {
		return organizations.saveAndFlush(new Organization("Org " + slug, slug, "Europe/Sarajevo", SADA));
	}

	@Test
	void istiKorisnikMozeImatiRazliciteUlogeURazlicitimOrganizacijama() {
		User mirza = users.saveAndFlush(new User("mirza@example.com", "hash", "Mirza", SADA));
		Organization teretana = organizacija("teretana-grbavica");
		Organization citaonica = organizacija("citaonica-fit");

		members.saveAndFlush(new OrganizationMember(teretana, mirza, OrganizationRole.PROVIDER, SADA));
		members.saveAndFlush(new OrganizationMember(citaonica, mirza, OrganizationRole.CLIENT, SADA));

		assertThat(members.findById(new OrganizationMember.Id(teretana.getId(), mirza.getId())))
				.get().extracting(OrganizationMember::getRole).isEqualTo(OrganizationRole.PROVIDER);
		assertThat(members.findById(new OrganizationMember.Id(citaonica.getId(), mirza.getId())))
				.get().extracting(OrganizationMember::getRole).isEqualTo(OrganizationRole.CLIENT);
	}

	@Test
	void bazaNePustaIstogKorisnikaDvaputUIstuOrganizaciju() {
		User mirza = users.saveAndFlush(new User("mirza@example.com", "hash", "Mirza", SADA));
		Organization teretana = organizacija("teretana-grbavica");
		members.saveAndFlush(new OrganizationMember(teretana, mirza, OrganizationRole.CLIENT, SADA));

		assertThatThrownBy(() -> jdbc.update(
				"insert into organization_members values (?, ?, 'ADMIN', now())", teretana.getId(), mirza.getId()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void bazaOdbijaNepoznatuUlogu() {
		User mirza = users.saveAndFlush(new User("mirza@example.com", "hash", "Mirza", SADA));
		Organization teretana = organizacija("teretana-grbavica");

		assertThatThrownBy(() -> jdbc.update(
				"insert into organization_members values (?, ?, 'SUPERADMIN', now())", teretana.getId(), mirza.getId()))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("organization_members_role");
	}

	@Test
	void bazaOdbijaSlugSVelikimSlovimaIRazmacima() {
		assertThatThrownBy(() -> jdbc.update(
				"insert into organizations values (gen_random_uuid(), 'X', 'Čitaonica FIT', 'Europe/Sarajevo', now())"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("organizations_slug_format");
	}

}
