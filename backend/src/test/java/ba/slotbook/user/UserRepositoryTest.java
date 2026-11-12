package ba.slotbook.user;

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

@Import(TestcontainersConfiguration.class)
@DataJpaTest
class UserRepositoryTest {

	private static final Instant SADA = Instant.parse("2026-11-12T10:00:00Z");

	@Autowired
	UserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void spremljenKorisnikSeNalaziPoEmailuIDobijaId() {
		users.saveAndFlush(new User("amra@example.com", "hash", "Amra Hodžić", SADA));

		User nadjen = users.findByEmail("amra@example.com").orElseThrow();

		assertThat(nadjen.getId()).isNotNull();
		assertThat(nadjen.getDisplayName()).isEqualTo("Amra Hodžić");
		assertThat(nadjen.getCreatedAt()).isEqualTo(SADA);
	}

	@Test
	void istiEmailSDrugacijimVelikimSlovimaSeNeMozeRegistrovatiDvaput() {
		users.saveAndFlush(new User("amra@example.com", "hash", "Amra Hodžić", SADA));

		assertThatThrownBy(() -> users.saveAndFlush(new User("Amra@Example.com", "hash", "Amra H.", SADA)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void bazaOdbijaEmailSVelikimSlovimaIakoGaJavaZaobide() {
		assertThatThrownBy(() -> jdbc.update("""
				insert into users (id, email, password_hash, display_name, created_at)
				values (gen_random_uuid(), 'Tarik@example.com', 'hash', 'Tarik', now())
				"""))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("users_email_mala_slova");
	}

}
