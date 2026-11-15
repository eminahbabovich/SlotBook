package ba.slotbook.stats;

import java.util.List;

// Popunjenost za Excel. Tačka-zarez, jer Excel s našim regionalnim postavkama zarez čita kao
// decimalni znak i sve stavi u jednu kolonu. BOM na početku, da Excel tekst čita kao UTF-8 i ne
// pokvari č i ć. Redovi završavaju s CRLF, po RFC 4180.
final class OccupancyCsv {

	private static final String BOM = "﻿";
	private static final String KRAJ_REDA = "\r\n";

	private OccupancyCsv() {
	}

	static String write(List<ResourceOccupancy> popunjenost) {
		StringBuilder csv = new StringBuilder(BOM)
				.append("Resurs;Zauzeto (min);Ponuđeno (min);Popunjenost (%)")
				.append(KRAJ_REDA);
		for (ResourceOccupancy resurs : popunjenost) {
			csv.append(polje(resurs.name())).append(';')
					.append(resurs.bookedMinutes()).append(';')
					.append(resurs.offeredMinutes()).append(';')
					.append(procenat(resurs))
					.append(KRAJ_REDA);
		}
		return csv.toString();
	}

	// Cijeli broj, a tačne brojke su u minutama. Bez ponuđenih minuta ćelija ostaje prazna.
	private static String procenat(ResourceOccupancy resurs) {
		if (resurs.offeredMinutes() == 0) {
			return "";
		}
		return String.valueOf(Math.round(resurs.bookedMinutes() * 100.0 / resurs.offeredMinutes()));
	}

	// Naziv unosi korisnik, pa može imati separator, navodnik ili novi red. Tada ide pod navodnike,
	// a navodnik unutra se udvostruči.
	private static String polje(String tekst) {
		// CSV injection: provider bi mogao nazvati resurs =HYPERLINK(...), a Excel bi to kod admina
		// izvršio kao formulu. Apostrof ispred Excel čita kao "ovo je tekst" i ne prikazuje ga.
		if (!tekst.isEmpty() && "=+-@\t\r".indexOf(tekst.charAt(0)) >= 0) {
			tekst = "'" + tekst;
		}
		if (tekst.contains(";") || tekst.contains("\"") || tekst.contains("\n") || tekst.contains("\r")) {
			return "\"" + tekst.replace("\"", "\"\"") + "\"";
		}
		return tekst;
	}

}
