package ba.slotbook.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class OccupancyCsvTest {

	@Test
	void zaglavljeIRedoviSuOdvojeniTackaZarezomSBomomICrlf() {
		String csv = OccupancyCsv.write(List.of(
				new ResourceOccupancy(UUID.randomUUID(), "Sto br. 1", 240, 840),
				new ResourceOccupancy(UUID.randomUUID(), "Kabinet 2-14", 60, 480)));

		// 240/840 je 28,57 %, a 60/480 tačno 12,5 %, što se zaokruži na 13
		assertThat(csv).isEqualTo("﻿Resurs;Zauzeto (min);Ponuđeno (min);Popunjenost (%)\r\n"
				+ "Sto br. 1;240;840;29\r\n"
				+ "Kabinet 2-14;60;480;13\r\n");
	}

	@Test
	void nazivSaSeparatoromIliNavodnikomIdePodNavodnike() {
		String csv = OccupancyCsv.write(List.of(
				new ResourceOccupancy(UUID.randomUUID(), "Sala; prvi sprat", 0, 60),
				new ResourceOccupancy(UUID.randomUUID(), "Sala \"Đulbašta\"", 0, 60)));

		assertThat(csv.lines().skip(1).toList()).containsExactly(
				"\"Sala; prvi sprat\";0;60;0",
				"\"Sala \"\"Đulbašta\"\"\";0;60;0");
	}

	// Resurs bez radnog vremena: dijeljenje nulom bi dalo NaN ili beskonačno
	@Test
	void bezPonudjenihMinutaProcenatOstajePrazan() {
		String csv = OccupancyCsv.write(List.of(new ResourceOccupancy(UUID.randomUUID(), "Sto br. 3", 0, 0)));

		assertThat(csv.lines().skip(1).toList()).containsExactly("Sto br. 3;0;0;");
	}

}
