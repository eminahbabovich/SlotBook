package ba.slotbook.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import ba.slotbook.TestcontainersConfiguration;

// Pravila koja čuva baza, i kad neko upiše red mimo Jave
@Import(TestcontainersConfiguration.class)
@DataJpaTest
class AvailabilityRuleRepositoryTest {

	@Autowired
	JdbcTemplate jdbc;

	private UUID sala;
	private UUID sauna;

	@BeforeEach
	void dvaResursaTeretane() {
		UUID lejla = UUID.randomUUID();
		UUID teretana = UUID.randomUUID();
		jdbc.update("insert into users values (?, 'lejla@example.com', 'hash', 'Lejla', now())", lejla);
		jdbc.update("insert into organizations values (?, 'Teretana', 'teretana-pravila', 'Europe/Sarajevo', now())",
				teretana);
		jdbc.update("insert into organization_members values (?, ?, 'PROVIDER', now())", teretana, lejla);
		sala = resurs(teretana, lejla, "Sala");
		sauna = resurs(teretana, lejla, "Sauna");
	}

	private UUID resurs(UUID organizacija, UUID provider, String naziv) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into resources (id, organization_id, provider_id, name, slot_minutes, created_at) "
				+ "values (?, ?, ?, ?, 60, now())", id, organizacija, provider, naziv);
		return id;
	}

	private void pravilo(UUID resurs, int dan, String od, String doo) {
		jdbc.update("insert into availability_rules values (gen_random_uuid(), ?, ?, ?::time, ?::time)", resurs, dan,
				od, doo);
	}

	@Test
	void intervaliKojiSeDodirujuIIstoVrijemeDrugogResursaProlaze() {
		pravilo(sala, 1, "08:00", "12:00");
		pravilo(sala, 1, "12:00", "16:00");
		pravilo(sala, 2, "08:00", "12:00");
		pravilo(sauna, 1, "08:00", "12:00");

		assertThat(jdbc.queryForObject("select count(*) from availability_rules", Long.class)).isEqualTo(4);
	}

	@Test
	void bazaOdbijaPreklapanjeIstogResursaIstogDana() {
		pravilo(sala, 1, "08:00", "12:00");

		assertThatThrownBy(() -> pravilo(sala, 1, "11:30", "13:00"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("availability_rules_bez_preklapanja");
	}

	// Svaki odbijen INSERT u svom testu: poslije greške Postgres ne izvršava ništa do kraja transakcije
	@Test
	void bazaOdbijaKrajPrijePocetka() {
		assertThatThrownBy(() -> pravilo(sala, 1, "22:00", "02:00"))
				.hasMessageContaining("availability_rules_pocetak_prije_kraja");
	}

	// Ista provjera za izuzetke: kraj mora biti poslije početka i kad se red upiše mimo Jave
	@Test
	void bazaOdbijaIzuzetakKojiZavrsavaPrijePocetka() {
		assertThatThrownBy(() -> jdbc.update("insert into time_off values (gen_random_uuid(), ?, "
				+ "'2035-12-27 00:00+01', '2035-12-24 00:00+01', 'Praznici', now())", sala))
				.hasMessageContaining("time_off_pocetak_prije_kraja");
	}

	@Test
	void bazaOdbijaDanVanSedmice() {
		assertThatThrownBy(() -> pravilo(sala, 8, "08:00", "12:00"))
				.hasMessageContaining("availability_rules_dan");
	}

}
