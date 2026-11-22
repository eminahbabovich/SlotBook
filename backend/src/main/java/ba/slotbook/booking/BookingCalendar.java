package ba.slotbook.booking;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

// Termini kao iCalendar (RFC 5545): jedan za prilog u mailu potvrde, a svi moji za pretplatu
// (CalendarFeedService). Ručno, jer su to četiri polja, a biblioteka bi bila veća od ovog fajla.
final class BookingCalendar {

	// Vrijeme u UTC-u (Z na kraju): kalendar ga sam prikaže u zoni korisnika, pa ne treba VTIMEZONE,
	// a termin preko prelaska na ljetno vrijeme ostaje tačan jer je Instant, ne lokalni sat.
	private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
			.withZone(ZoneOffset.UTC);
	private static final int NAJDUZI_RED = 75;

	// Pretplata pokazuje ono što drži termin i ono što se desilo. Otkazana, odbijena i istekla
	// nestanu iz kalendara pri sljedećem osvježavanju, jer ih u fajlu više nema.
	static final Set<BookingStatus> U_KALENDARU = EnumSet.of(BookingStatus.PENDING, BookingStatus.CONFIRMED,
			BookingStatus.COMPLETED);

	private BookingCalendar() {
	}

	static String ics(Booking rezervacija, Instant sada) {
		return red("BEGIN:VCALENDAR")
				+ red("VERSION:2.0")
				+ red("PRODID:-//SlotBook//SlotBook//BS")
				+ red("METHOD:PUBLISH")
				+ dogadjaj(rezervacija, sada, null)
				+ red("END:VCALENDAR");
	}

	// Kalendar sam povlači fajl ponovo. REFRESH-INTERVAL je iz RFC 7986, a X-PUBLISHED-TTL čita Outlook.
	// To su samo prijedlozi: koliko često stvarno osvježava odlučuje aplikacija (Google rijetko).
	static String feed(List<Booking> rezervacije, Instant sada) {
		StringBuilder fajl = new StringBuilder()
				.append(red("BEGIN:VCALENDAR"))
				.append(red("VERSION:2.0"))
				.append(red("PRODID:-//SlotBook//SlotBook//BS"))
				.append(red("METHOD:PUBLISH"))
				.append(red("X-WR-CALNAME:SlotBook"))
				.append(red("REFRESH-INTERVAL;VALUE=DURATION:PT1H"))
				.append(red("X-PUBLISHED-TTL:PT1H"));
		for (Booking rezervacija : rezervacije) {
			// Na čekanju je TENTATIVE: kalendar je prikaže kao neodlučenu, a ne kao siguran termin
			fajl.append(dogadjaj(rezervacija, sada,
					rezervacija.getStatus() == BookingStatus.PENDING ? "TENTATIVE" : "CONFIRMED"));
		}
		return fajl.append(red("END:VCALENDAR")).toString();
	}

	private static String dogadjaj(Booking rezervacija, Instant sada, String status) {
		return red("BEGIN:VEVENT")
				// Isti UID za istu rezervaciju: kalendar prepozna termin ako stigne ponovo
				+ red("UID:" + rezervacija.getId() + "@slotbook")
				+ red("DTSTAMP:" + UTC.format(sada))
				+ red("DTSTART:" + UTC.format(rezervacija.getStartTime()))
				+ red("DTEND:" + UTC.format(rezervacija.getEndTime()))
				+ red("SUMMARY:" + tekst(rezervacija.getResource().getName()))
				+ red("LOCATION:" + tekst(rezervacija.getResource().getOrganization().getName()))
				+ (status == null ? "" : red("STATUS:" + status))
				+ red("END:VEVENT");
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
