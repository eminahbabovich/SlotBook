package ba.slotbook.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.organization.Organization;
import ba.slotbook.user.User;
import ba.slotbook.user.UserRepository;
import jakarta.persistence.EntityManager;

@Import(TestcontainersConfiguration.class)
@DataJpaTest
class ResourceRepositoryTest {

	private static final Instant SADA = Instant.parse("2026-11-13T10:00:00Z");

	@Autowired
	ResourceRepository resources;

	@Autowired
	EntityManager em;

	@Autowired
	UserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	private User lejla;
	private Organization teretana;
	private Organization citaonica;

	// Lejla je trenerica u teretani, a u čitaonici nije članica
	@BeforeEach
	void podaci() {
		lejla = users.saveAndFlush(new User("lejla@example.com", "hash", "Lejla", SADA));
		teretana = organizacija("teretana-grbavica");
		citaonica = organizacija("citaonica-fit");
		jdbc.update("insert into organization_members values (?, ?, 'PROVIDER', now())",
				teretana.getId(), lejla.getId());
	}

	private Organization organizacija(String slug) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into organizations values (?, ?, ?, 'Europe/Sarajevo', now())", id, "Org " + slug, slug);
		return em.getReference(Organization.class, id);
	}

	@Test
	void clanOrganizacijeMozeVoditiResurs() {
		Resource sala = resources.saveAndFlush(
				new Resource(teretana, lejla, "  Sala za funkcionalni trening ", null, 60, true, 0, SADA));
		// Bez ovoga findById vrati isti objekat iz memorije, a ne red pročitan iz baze
		em.clear();

		assertThat(resources.findById(sala.getId())).get()
				.satisfies(r -> assertThat(r.getName()).isEqualTo("Sala za funkcionalni trening"))
				.satisfies(r -> assertThat(r.isActive()).isTrue());
	}

	@Test
	void bazaNePustaProvideraKojiNijeClanTeOrganizacije() {
		assertThatThrownBy(() -> resources.saveAndFlush(
				new Resource(citaonica, lejla, "Sto 4 u čitaonici", null, 120, true, 0, SADA)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("resources_provider_clan");
	}

	@Test
	void bazaOdbijaTrajanjeTerminaVanGranica() {
		assertThatThrownBy(() -> resources.saveAndFlush(
				new Resource(teretana, lejla, "Sauna", null, 0, true, 0, SADA)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("resources_slot_minutes");
	}

}
