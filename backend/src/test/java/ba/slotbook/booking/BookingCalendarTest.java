package ba.slotbook.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class BookingCalendarTest {

	private static final ZoneId SARAJEVO = ZoneId.of("Europe/Sarajevo");
	private static final Instant SADA = Instant.parse("2036-03-20T10:00:00Z");

	private static Booking termin(String resurs, LocalDateTime pocetakUSarajevu) {
		Booking rezervacija = mock(Booking.class, RETURNS_DEEP_STUBS);
		Instant pocetak = pocetakUSarajevu.atZone(SARAJEVO).toInstant();
		when(rezervacija.getId()).thenReturn(UUID.fromString("7b0e5c4a-2f1d-4c3e-9a8b-1d2e3f4a5b6c"));
		when(rezervacija.getStartTime()).thenReturn(pocetak);
		when(rezervacija.getEndTime()).thenReturn(pocetak.plusSeconds(3600));
		when(rezervacija.getResource().getName()).thenReturn(resurs);
		when(rezervacija.getResource().getOrganization().getName()).thenReturn("Teretana Grbavica");
		return rezervacija;
	}

	// Isti lokalni sat (09:00) prije i poslije 30.3.2036, kad Sarajevo prelazi na ljetno vrijeme.
	// U UTC-u se pomjeri za sat, pa kalendar korisnika oba prikaže u 09:00.
	@Test
	void terminUDevetPrijeIPoslijeLjetnogVremenaJeUUtcuSatRazlike() {
		String zimi = BookingCalendar.ics(termin("Sala za grupne treninge", LocalDateTime.of(2036, 3, 24, 9, 0)), SADA);
		String ljeti = BookingCalendar.ics(termin("Sala za grupne treninge", LocalDateTime.of(2036, 3, 31, 9, 0)), SADA);

		assertThat(zimi).contains("DTSTART:20360324T080000Z\r\n", "DTEND:20360324T090000Z\r\n");
		assertThat(ljeti).contains("DTSTART:20360331T070000Z\r\n", "DTEND:20360331T080000Z\r\n");
	}

	@Test
	void zarezITackaZarezUNazivuSeEscapeaju() {
		String ics = BookingCalendar.ics(termin("Sala 2, prvi sprat; ulaz iz dvorišta", LocalDateTime.of(2036, 3, 24, 9, 0)),
				SADA);

		assertThat(ics).contains("SUMMARY:Sala 2\\, prvi sprat\\; ulaz iz dvorišta\r\n")
				.startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
	}

	// RFC 5545 broji bajtove, a č, ć, š, đ, ž su po dva. Brojanje znakova bi dalo redove od 100+ bajtova.
	@Test
	void dugNazivSNasimSlovimaSeLomiNa75BajtovaINeGubiNijednoSlovo() {
		String naziv = "Čitaonica Šumarskog fakulteta, đački čošak uz žičaru, ćelija broj četiri ".repeat(2).strip();
		String ics = BookingCalendar.ics(termin(naziv, LocalDateTime.of(2036, 3, 24, 9, 0)), SADA);

		assertThat(Arrays.stream(ics.split("\r\n")).mapToInt(red -> red.getBytes(StandardCharsets.UTF_8).length).max()
				.getAsInt()).isLessThanOrEqualTo(75);
		assertThat(ics.replace("\r\n ", "")).contains("SUMMARY:" + naziv.replace(",", "\\,") + "\r\n");
	}

	// Prazna pretplata (korisnik još nema termina) mora i dalje biti ispravan kalendar, inače ga Google odbije
	@Test
	void pretplataBezTerminaJeIspravanPrazanKalendarSImenomIOsvjezavanjem() {
		String ics = BookingCalendar.feed(List.of(), SADA);

		assertThat(ics).isEqualTo("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//SlotBook//SlotBook//BS\r\n"
				+ "METHOD:PUBLISH\r\nX-WR-CALNAME:SlotBook\r\nREFRESH-INTERVAL;VALUE=DURATION:PT1H\r\n"
				+ "X-PUBLISHED-TTL:PT1H\r\nEND:VCALENDAR\r\n");
	}

	@Test
	void uPretplatiPotvrdjenaImaStatusConfirmedAPrilogUMailuNemaStatus() {
		Booking potvrdjena = termin("Sala za grupne treninge", LocalDateTime.of(2036, 3, 24, 9, 0));
		when(potvrdjena.getStatus()).thenReturn(BookingStatus.CONFIRMED);

		assertThat(BookingCalendar.feed(List.of(potvrdjena), SADA)).contains("STATUS:CONFIRMED\r\nEND:VEVENT\r\n");
		assertThat(BookingCalendar.ics(potvrdjena, SADA)).doesNotContain("STATUS:");
	}

}
