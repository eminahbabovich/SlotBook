package ba.slotbook.resource;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class ResourceTest {

	private Resource resurs(String naziv, String opis) {
		return new Resource(null, null, naziv, opis, 60, true, 0, Instant.parse("2026-11-13T10:00:00Z"));
	}

	@Test
	void nazivIOpisSeCisteOdRazmakaSaStrana() {
		Resource sala = resurs("  Sala 2 ", "\tDrugi sprat, pored svlačionice  ");

		assertThat(sala.getName()).isEqualTo("Sala 2");
		assertThat(sala.getDescription()).isEqualTo("Drugi sprat, pored svlačionice");
	}

	@Test
	void prazanOpisSeCuvaKaoDaOpisaNema() {
		assertThat(resurs("Sauna", "   ").getDescription()).isNull();
		assertThat(resurs("Sauna", "").getDescription()).isNull();
	}

	@Test
	void izmjenaCistiPodatkeKaoIKreiranje() {
		Resource sala = resurs("Sala 2", "Drugi sprat");

		sala.update(" Sala 3 ", "  ", 45, false, 24);

		assertThat(sala.getName()).isEqualTo("Sala 3");
		assertThat(sala.getDescription()).isNull();
		assertThat(sala.getSlotMinutes()).isEqualTo(45);
		assertThat(sala.isAutoConfirm()).isFalse();
		assertThat(sala.getCancelNoticeHours()).isEqualTo(24);
	}

	@Test
	void noviResursJeAktivanDokSeNeDeaktivira() {
		Resource sauna = resurs("Sauna", null);
		assertThat(sauna.isActive()).isTrue();

		sauna.deactivate();
		assertThat(sauna.isActive()).isFalse();
	}

}
