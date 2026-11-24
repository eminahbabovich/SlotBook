package ba.slotbook.payment;

import static ba.slotbook.payment.StripeTestDogadjaji.dogadjaj;
import static ba.slotbook.payment.StripeTestDogadjaji.potpisan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.booking.BookingService;
import ba.slotbook.user.UserRepository;

// Šta se dešava s novcem i Checkout sesijom kad rezervacija pusti termin
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PaymentRefundApiTest {

	private static final String LOZINKA = "bascarsija-sebilj-1753";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	LazniStripeCheckout stripe;

	@Autowired
	BookingService bookingService;

	private String amra;
	private String hana;
	private UUID teretana;

	@BeforeEach
	void teretanaSaClanicom() throws Exception {
		amra = token("amra.povrat@example.com", "Amra");
		hana = token("hana.povrat@example.com", "Hana");
		var postojeca = jdbc.queryForList("select id from organizations where slug = 'teretana-povrat'", UUID.class);
		teretana = postojeca.isEmpty() ? novaOrganizacija() : postojeca.getFirst();
		clanica("hana.povrat@example.com");
		stripe.neRadi(false);
	}

	@Test
	void klijentkaOtkazujePlacenuRezervacijuIDobijaNovacNazad() throws Exception {
		Placanje placanje = placena(hana);

		otkazi(hana, placanje).andExpect(status().isOk());

		assertThat(stripe.povratiZa(placanje.paymentIntent())).hasSize(1);
		assertThat(statusUplate(placanje)).isEqualTo("REFUNDED");
		assertThat(statusRezervacije(placanje)).isEqualTo("CANCELLED");
	}

	// Trener je bolestan i otkaže trening. Klijentica nije kriva, pa dobija sve nazad.
	@Test
	void osobljeOtkazujePlacenuRezervacijuINovacSeVraca() throws Exception {
		Placanje placanje = placena(hana);

		otkazi(amra, placanje).andExpect(status().isOk());

		assertThat(stripe.povratiZa(placanje.paymentIntent())).hasSize(1);
		assertThat(statusUplate(placanje)).isEqualTo("REFUNDED");
	}

	// Inače bi rezervacija bila otkazana, a novac ostao kod nas, i to bez traga da treba vratiti
	@Test
	void kadStripeNeVratiNovacOtkazivanjeNeProlaziIRezervacijaOstajePotvrdjena() throws Exception {
		Placanje placanje = placena(hana);
		stripe.neRadi(true);

		otkazi(hana, placanje).andExpect(status().isServiceUnavailable());

		assertThat(statusRezervacije(placanje)).isEqualTo("CONFIRMED");
		assertThat(statusUplate(placanje)).isEqualTo("PAID");
	}

	// Klijentica je otvorila Checkout pa se predomislila. Stranica se više ne može platiti.
	@Test
	void otkazanaRezervacijaKojaCekaUplatuZatvaraCheckoutBezPovrata() throws Exception {
		Placanje placanje = otvorena(hana);

		otkazi(hana, placanje).andExpect(status().isOk());

		assertThat(stripe.zatvorena(placanje.sesija())).isTrue();
		assertThat(statusUplate(placanje)).isEqualTo("EXPIRED");
	}

	@Test
	void odbijenaRezervacijaKojaCekaUplatuZatvaraCheckout() throws Exception {
		Placanje placanje = otvorena(hana);

		mockMvc.perform(post("/api/bookings/{id}/reject", placanje.rezervacija()).header("Authorization", "Bearer " + amra))
				.andExpect(status().isOk());

		assertThat(stripe.zatvorena(placanje.sesija())).isTrue();
		assertThat(statusUplate(placanje)).isEqualTo("EXPIRED");
	}

	// Rok za uplatu je prošao, posao istekne rezervaciju i zatvori sesiju koja bi trajala još minutu
	@Test
	void istekNeplaceneRezervacijeZatvaraCheckout() throws Exception {
		Placanje placanje = otvorena(hana);
		jdbc.update("update bookings set expires_at = now() - interval '1 minute' where id = ?", placanje.rezervacija());

		bookingService.expireOverdue();

		assertThat(statusRezervacije(placanje)).isEqualTo("EXPIRED");
		assertThat(stripe.zatvorena(placanje.sesija())).isTrue();
		assertThat(statusUplate(placanje)).isEqualTo("EXPIRED");
	}

	// Zatvaranje je samo pristojnost. Ako ne uspije, otkazivanje ipak prolazi, a uplatu koja eventualno
	// stigne vrati webhook (StripeWebhookApiTest).
	@Test
	void kadSesijaNeMozeBitiZatvorenaOtkazivanjeIpakProlazi() throws Exception {
		Placanje placanje = otvorena(hana);
		stripe.neRadi(true);

		otkazi(hana, placanje).andExpect(status().isOk());

		assertThat(statusRezervacije(placanje)).isEqualTo("CANCELLED");
		assertThat(statusUplate(placanje)).isEqualTo("OPEN");
	}

	// Brisanje naloga otkazuje buduće rezervacije jednim SQL-om, mimo BookingService
	@Test
	void brisanjeNalogaVracaNovacZaBuducuPlacenuRezervaciju() throws Exception {
		String email = "lejla." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
		String lejla = token(email, "Lejla");
		clanica(email);
		Placanje placanje = placena(lejla);

		mockMvc.perform(delete("/api/me").header("Authorization", "Bearer " + lejla)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"password": "%s"}
						""".formatted(LOZINKA)))
				.andExpect(status().is2xxSuccessful());

		assertThat(statusRezervacije(placanje)).isEqualTo("CANCELLED");
		assertThat(stripe.povratiZa(placanje.paymentIntent())).hasSize(1);
		assertThat(statusUplate(placanje)).isEqualTo("REFUNDED");
	}

	private record Placanje(UUID rezervacija, String sesija, String paymentIntent) {
	}

	// Rezervacija u sali od 15 KM i otvoren Checkout (lažni Stripe daje sesiju)
	private Placanje otvorena(String token) throws Exception {
		UUID sala = idIz(mockMvc.perform(post("/api/organizations/{id}/resources", teretana)
				.header("Authorization", "Bearer " + amra)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Sala %s", "slotMinutes": 60, "autoConfirm": true, "priceCents": 1500}
						""".formatted(UUID.randomUUID().toString().substring(0, 8)))));
		mockMvc.perform(put("/api/resources/{id}/availability", sala)
				.header("Authorization", "Bearer " + amra)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"rules": [{"dayOfWeek": "MONDAY", "start": "08:00", "end": "12:00"}]}
						"""))
				.andExpect(status().isOk());
		UUID rezervacija = idIz(mockMvc.perform(post("/api/resources/{id}/bookings", sala)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"startTime": "2035-11-19T09:00:00+01:00"}
						""")));
		mockMvc.perform(post("/api/bookings/{id}/pay", rezervacija).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
		String sesija = jdbc.queryForObject("select stripe_session_id from payments where booking_id = ?",
				String.class, rezervacija);
		return new Placanje(rezervacija, sesija, "pi_" + rezervacija.toString().substring(0, 8));
	}

	// Isto, pa uplata stigne webhookom i rezervacija je potvrđena
	private Placanje placena(String token) throws Exception {
		Placanje placanje = otvorena(token);
		var dostava = potpisan(dogadjaj("evt_" + placanje.rezervacija(), StripeDogadjaj.PLACENO, placanje.sesija(),
				placanje.paymentIntent(), "paid"));
		mockMvc.perform(post("/api/stripe/webhook").contentType(MediaType.APPLICATION_JSON).content(dostava.tijelo())
				.header("Stripe-Signature", dostava.potpis())).andExpect(status().isOk());
		assertThat(statusRezervacije(placanje)).isEqualTo("CONFIRMED");
		return placanje;
	}

	private ResultActions otkazi(String token, Placanje placanje) throws Exception {
		return mockMvc.perform(post("/api/bookings/{id}/cancel", placanje.rezervacija())
				.header("Authorization", "Bearer " + token));
	}

	private String statusRezervacije(Placanje placanje) {
		return jdbc.queryForObject("select status from bookings where id = ?", String.class, placanje.rezervacija());
	}

	private String statusUplate(Placanje placanje) {
		return jdbc.queryForObject("select status from payments where stripe_session_id = ?", String.class,
				placanje.sesija());
	}

	private void clanica(String email) {
		jdbc.update("insert into organization_members values (?, ?, 'CLIENT', now()) on conflict do nothing", teretana,
				users.findByEmail(email).orElseThrow().getId());
	}

	private UUID idIz(ResultActions odgovor) throws Exception {
		return UUID.fromString(JsonPath.read(odgovor.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), "$.id"));
	}

	private String token(String email, String ime) throws Exception {
		if (users.findByEmail(email).isEmpty()) {
			mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
					{"email": "%s", "password": "%s", "displayName": "%s"}
					""".formatted(email, LOZINKA, ime))).andExpect(status().isAccepted());
			jdbc.update("update users set email_verified_at = now() where email = lower(?)", email);
		}
		String tijelo = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "%s"}
				""".formatted(email, LOZINKA))).andReturn().getResponse().getContentAsString();
		return JsonPath.read(tijelo, "$.accessToken");
	}

	private UUID novaOrganizacija() throws Exception {
		return idIz(mockMvc.perform(post("/api/organizations")
				.header("Authorization", "Bearer " + amra)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Teretana Dobrinja", "slug": "teretana-povrat", "timezone": "Europe/Sarajevo"}
						""")));
	}

}
