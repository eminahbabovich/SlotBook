package ba.slotbook.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import ba.slotbook.organization.MyOrganizationResponse;
import ba.slotbook.organization.OrganizationRole;

// Model sam računa "sutra" iz datuma u uputstvu, pa taj datum mora biti lokalni dan organizacije
class ChatServiceTest {

	private static final MyOrganizationResponse CITAONICA = new MyOrganizationResponse(UUID.randomUUID(),
			"Čitaonica FIT", "citaonica-fit", "Europe/Sarajevo", OrganizationRole.CLIENT, null);

	@Test
	void uputstvoImaLokalniDanIKadJeUUtcJosJucer() {
		// 23:30 UTC u subotu je 00:30 u nedjelju po Sarajevu, noć kad se pomjera sat
		String uputstvo = servis("2035-03-24T23:30:00Z").uputstvo(CITAONICA);

		assertThat(uputstvo).contains("Sunday, 2035-03-25, 00:30").contains("Europe/Sarajevo").contains("Čitaonica FIT");
	}

	@Test
	void uputstvoPoslijePomjeranjaSataImaLjetniSat() {
		// 08:00 UTC je od 25.3. u 10:00 po Sarajevu, a ne u 09:00
		assertThat(servis("2035-03-26T08:00:00Z").uputstvo(CITAONICA)).contains("Monday, 2035-03-26, 10:00");
	}

	// U mjerenju je gpt-oss-120b sam izračunao da je 28.11.2026. ponedjeljak (subota je). Zato dobija
	// spisak dana s nazivima, da ih prepiše, a ne računa.
	@Test
	void uputstvoImaNaredneDaneSBosanskimNazivimaOdLokalnogDana() {
		String uputstvo = servis("2026-11-27T23:30:00Z").uputstvo(CITAONICA);

		// 23:30 UTC u petak je već subota po Sarajevu, pa spisak kreće od subote
		assertThat(uputstvo).contains("subota 2026-11-28, nedjelja 2026-11-29, ponedjeljak 2026-11-30")
				.contains("petak 2026-12-11")
				.doesNotContain("petak 2026-11-27");
		// Bez ove rečenice je model u drugom mjerenju odbio 28. mart 2027. kao "van 14 dana"
		assertThat(uputstvo).contains("is not a limit");
	}

	private static ChatService servis(String sada) {
		return new ChatService(null, null, null, null, null, null, null,
				Clock.fixed(Instant.parse(sada), ZoneOffset.UTC));
	}

}
