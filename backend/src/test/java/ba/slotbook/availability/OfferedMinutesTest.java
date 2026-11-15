package ba.slotbook.availability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.config.ClockConfig;

// Ponuđene minute za statistiku, s pravilima i izuzecima iz baze
@Import({ TestcontainersConfiguration.class, SlotService.class, ClockConfig.class })
@DataJpaTest
class OfferedMinutesTest {

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	SlotService slotService;

	// Pet sedmica novembra 2025. To je prošlost, duže je od 31 dan koliko smije kalendar, a resurs je
	// u međuvremenu deaktiviran. Sve to statistika mora preživjeti.
	@Test
	void prosliPeriodDuziOdMjesecaNaDeaktiviranomResursuBrojiSvePonudjeneTermine() {
		UUID sto = deaktiviranStoUCitaonici();
		// Ponedjeljkom 4 satna termina, srijedom 2
		jdbc.update("insert into availability_rules values (gen_random_uuid(), ?, 1, '08:00'::time, '12:00'::time)", sto);
		jdbc.update("insert into availability_rules values (gen_random_uuid(), ?, 3, '14:00'::time, '16:00'::time)", sto);
		// Sat u srijedu 5.11. zatvoren zbog sastanka
		jdbc.update("insert into time_off values (gen_random_uuid(), ?, '2025-11-05 14:00+01', '2025-11-05 15:00+01', "
				+ "'Sastanak', now())", sto);

		// 3.11. do 8.12.: pet ponedjeljaka (20 termina) i pet srijeda (10), minus jedan termin = 29 sati
		long minute = slotService.offeredMinutes(sto, LocalDate.parse("2025-11-03"), LocalDate.parse("2025-12-08"));

		assertThat(minute).isEqualTo(29 * 60);
	}

	private UUID deaktiviranStoUCitaonici() {
		UUID selma = UUID.randomUUID();
		jdbc.update("insert into users values (?, 'selma@example.com', 'hash', 'Selma', now())", selma);
		UUID citaonica = UUID.randomUUID();
		jdbc.update("insert into organizations values (?, 'Čitaonica FIT', 'citaonica-fit', 'Europe/Sarajevo', now())",
				citaonica);
		jdbc.update("insert into organization_members values (?, ?, 'PROVIDER', now())", citaonica, selma);
		UUID sto = UUID.randomUUID();
		jdbc.update("insert into resources (id, organization_id, provider_id, name, slot_minutes, active, created_at) "
				+ "values (?, ?, ?, 'Sto br. 1', 60, false, now())", sto, citaonica, selma);
		return sto;
	}

}
