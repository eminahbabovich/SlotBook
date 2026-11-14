package ba.slotbook.booking;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

// Jedan termin kao iCalendar (RFC 5545), za prilog u mailu potvrde. Ručno, jer su to četiri
// polja, a biblioteka bi bila veća od ovog fajla.
final class BookingCalendar {

	// Vrijeme u UTC-u (Z na kraju): kalendar ga sam prikaže u zoni korisnika, pa ne treba VTIMEZONE,
	// a termin preko prelaska na ljetno vrijeme ostaje tačan jer je Instant, ne lokalni sat.
	private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
			.withZone(ZoneOffset.UTC);
	private static final int NAJDUZI_RED = 75;

	private BookingCalendar() {
	}

	static String ics(Booking rezervacija, Instant sada) {
		return red("BEGIN:VCALENDAR")
				+ red("VERSION:2.0")
				+ red("PRODID:-//SlotBook//SlotBook//BS")
				+ red("METHOD:PUBLISH")
				+ red("BEGIN:VEVENT")
				// Isti UID za istu rezervaciju: kalendar prepozna termin ako stigne ponovo
				+ red("UID:" + rezervacija.getId() + "@slotbook")
				+ red("DTSTAMP:" + UTC.format(sada))
				+ red("DTSTART:" + UTC.format(rezervacija.getStartTime()))
				+ red("DTEND:" + UTC.format(rezervacija.getEndTime()))
				+ red("SUMMARY:" + tekst(rezervacija.getResource().getName()))
				+ red("LOCATION:" + tekst(rezervacija.getResource().getOrganization().getName()))
				+ red("END:VEVENT")
				+ red("END:VCALENDAR");
	}

	// Zarez, tačka-zarez i obrnuta kosa crta znače nešto u formatu, pa idu s \ ispred
	private static String tekst(String vrijednost) {
		return vrijednost.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n");
	}

	// Red duži od 75 bajtova se lomi, a nastavak počinje razmakom. Broje se bajtovi, ne znakovi,
	// i lomi se između znakova: č je dva bajta i ne smije se prepoloviti.
	private static String red(String sadrzaj) {
		StringBuilder rezultat = new StringBuilder();
		int bajtova = 0;
		for (int i = 0; i < sadrzaj.length(); ) {
			int znak = sadrzaj.codePointAt(i);
			int duzina = new String(Character.toChars(znak)).getBytes(StandardCharsets.UTF_8).length;
			if (bajtova + duzina > NAJDUZI_RED) {
				rezultat.append("\r\n ");
				bajtova = 1;
			}
			rezultat.appendCodePoint(znak);
			bajtova += duzina;
			i += Character.charCount(znak);
		}
		return rezultat.append("\r\n").toString();
	}

}
