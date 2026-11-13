package ba.slotbook.availability;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import ba.slotbook.error.InvalidFieldException;

class SlotServiceTest {

	private static final LocalDate PONEDJELJAK = LocalDate.parse("2026-11-16");

	@Test
	void jedanDanJeOdDanasDoSutra() {
		assertThatCode(() -> SlotService.provjeri(PONEDJELJAK, PONEDJELJAK.plusDays(1))).doesNotThrowAnyException();
	}

	@Test
	void istiDatumIliObrnutPeriodSeOdbijaju() {
		assertThatThrownBy(() -> SlotService.provjeri(PONEDJELJAK, PONEDJELJAK))
				.isInstanceOf(InvalidFieldException.class);
		assertThatThrownBy(() -> SlotService.provjeri(PONEDJELJAK, PONEDJELJAK.minusDays(1)))
				.isInstanceOf(InvalidFieldException.class);
	}

	// Granica je tačno 31 dan: prolazi, a dan više ne
	@Test
	void periodMozeBitiNajviseTridesetJedanDan() {
		assertThatCode(() -> SlotService.provjeri(PONEDJELJAK, PONEDJELJAK.plusDays(31))).doesNotThrowAnyException();
		assertThatThrownBy(() -> SlotService.provjeri(PONEDJELJAK, PONEDJELJAK.plusDays(32)))
				.isInstanceOf(InvalidFieldException.class)
				.hasFieldOrPropertyWithValue("polje", "to");
	}

}
