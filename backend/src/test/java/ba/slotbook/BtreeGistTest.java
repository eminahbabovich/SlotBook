package ba.slotbook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BtreeGistTest {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void migracijaInstalirajeEkstenziju() {
		Integer broj = jdbc.queryForObject(
				"select count(*) from pg_extension where extname = 'btree_gist'", Integer.class);

		assertThat(broj).isEqualTo(1);
	}

	@Test
	@Transactional
	void istiResursSePreklapaUVremenu() {
		napraviProbnuTabelu();
		dodaj(1, "2026-03-29 09:00+00", "2026-03-29 10:00+00");

		assertThatThrownBy(() -> dodaj(1, "2026-03-29 09:30+00", "2026-03-29 10:30+00"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@Transactional
	void terminiKojiSeDodirujuSuDozvoljeni() {
		napraviProbnuTabelu();
		dodaj(1, "2026-03-29 09:00+00", "2026-03-29 10:00+00");

		dodaj(1, "2026-03-29 10:00+00", "2026-03-29 11:00+00");

		assertThat(broj()).isEqualTo(2);
	}

	@Test
	@Transactional
	void drugiResursMozeUIstoVrijeme() {
		napraviProbnuTabelu();
		dodaj(1, "2026-03-29 09:00+00", "2026-03-29 10:00+00");

		dodaj(2, "2026-03-29 09:00+00", "2026-03-29 10:00+00");

		assertThat(broj()).isEqualTo(2);
	}

	private void napraviProbnuTabelu() {
		jdbc.execute("""
				create temp table probna_rezervacija (
					resource_id bigint not null,
					termin tstzrange not null,
					exclude using gist (resource_id with =, termin with &&)
				)""");
	}

	private void dodaj(long resourceId, String pocetak, String kraj) {
		jdbc.update("insert into probna_rezervacija values (?, tstzrange(?::timestamptz, ?::timestamptz))",
				resourceId, pocetak, kraj);
	}

	private int broj() {
		return jdbc.queryForObject("select count(*) from probna_rezervacija", Integer.class);
	}

}
