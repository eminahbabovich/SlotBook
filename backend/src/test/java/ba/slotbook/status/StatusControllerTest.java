package ba.slotbook.status;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class StatusControllerTest {

	@Test
	void vracaVrijemeSaSataKojiMuJeDan() {
		Instant zamrznuto = Instant.parse("2026-05-14T08:15:00Z");
		Clock sat = Clock.fixed(zamrznuto, ZoneOffset.UTC);

		StatusResponse odgovor = new StatusController(sat).status();

		assertThat(odgovor.aplikacija()).isEqualTo("slotbook");
		assertThat(odgovor.vrijeme()).isEqualTo(zamrznuto);
	}

}
