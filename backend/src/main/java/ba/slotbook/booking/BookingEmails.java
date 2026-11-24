package ba.slotbook.booking;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import ba.slotbook.notification.EmailContent;
import ba.slotbook.notification.NotificationType;

// Tekst emaila nastaje zajedno s obavijesti. Vrijeme je u zoni organizacije, jer na termin se
// dolazi po lokalnom satu, a ne po UTC-u.
final class BookingEmails {

	private static final DateTimeFormatter KAD = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy. 'u' HH:mm",
			Locale.forLanguageTag("bs"));

	private BookingEmails() {
	}

	static EmailContent za(NotificationType tip, Booking rezervacija, Instant sada) {
		ZoneId zona = ZoneId.of(rezervacija.getResource().getOrganization().getTimezone());
		String resurs = rezervacija.getResource().getName();
		String termin = kad(rezervacija.getStartTime(), zona);
		return switch (tip) {
			case BOOKING_AWAITS_CONFIRMATION -> new EmailContent("Rezervacija čeka potvrdu: " + resurs,
					rezervacija.getClient().getDisplayName() + " želi termin " + termin + " (" + resurs
							+ "). Potvrdi ili odbij rezervaciju u aplikaciji do " + kad(rezervacija.getExpiresAt(), zona)
							+ ". Poslije toga ističe sama i termin je ponovo slobodan.");
			case BOOKING_CONFIRMED -> new EmailContent("Rezervacija potvrđena: " + resurs, "Termin " + termin + " ("
					+ resurs + ") je tvoj. U prilogu je termin za kalendar. Ako ne možeš doći, otkaži ga u aplikaciji, "
					+ "da ga može uzeti neko drugi.", BookingCalendar.ics(rezervacija, sada));
			case BOOKING_REJECTED -> new EmailContent("Rezervacija odbijena: " + resurs, "Rezervacija za termin "
					+ termin + " (" + resurs + ") nije prihvaćena. Slobodne termine vidiš u aplikaciji.");
			case BOOKING_CANCELLED -> new EmailContent("Rezervacija otkazana: " + resurs,
					"Rezervacija za termin " + termin + " (" + resurs + ") je otkazana i termin je ponovo slobodan.");
			case BOOKING_EXPIRED -> new EmailContent("Rezervacija istekla: " + resurs, "Rezervacija za termin "
					+ termin + " (" + resurs + ") nije " + (rezervacija.getPriceCents() > 0 ? "plaćena" : "potvrđena")
					+ " na vrijeme, pa je istekla. Slobodne termine vidiš u aplikaciji.");
			case BOOKING_REMINDER -> new EmailContent("Podsjetnik: " + resurs, "Podsjetnik za termin " + termin
					+ " (" + resurs + "). Ako ne možeš doći, otkaži ga u aplikaciji, da ga može uzeti neko drugi.");
			// Bez default grane, da novi tip obavijesti o rezervaciji ne prođe kompajler bez teksta
			case EMAIL_VERIFICATION, PASSWORD_RESET, ORGANIZATION_INVITE, ACCOUNT_EXISTS, WAITLIST_OFFER -> throw new IllegalArgumentException("Nije obavijest o rezervaciji: " + tip);
		};
	}

	// Link vodi pravo na kalendar resursa, gdje je ponuđeni termin slobodan samo za ovog klijenta
	static EmailContent ponuda(WaitlistEntry upis, Instant rok, String appUrl) {
		var organizacija = upis.getResource().getOrganization();
		ZoneId zona = ZoneId.of(organizacija.getTimezone());
		String resurs = upis.getResource().getName();
		String link = appUrl + "/o/" + organizacija.getSlug() + "/r/" + upis.getResource().getId();
		return new EmailContent("Termin se oslobodio: " + resurs, "Termin " + kad(upis.getStartTime(), zona) + " ("
				+ resurs + ") se oslobodio, a ti si prvi na listi čekanja. Do " + kad(rok, zona)
				+ " ga niko drugi ne može rezervisati.\n\nRezerviši ga ovdje: " + link
				+ "\n\nAko ga do tada ne rezervišeš, termin se nudi sljedećem na listi.");
	}

	private static String kad(Instant trenutak, ZoneId zona) {
		return KAD.format(trenutak.atZone(zona));
	}

}
