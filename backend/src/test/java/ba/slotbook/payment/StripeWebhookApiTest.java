package ba.slotbook.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
import com.stripe.net.Webhook;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.user.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class StripeWebhookApiTest {

	// Ista kao slotbook.stripe.webhook-secret u TestcontainersConfiguration
	private static final String TAJNA = "whsec_testna_tajna_samo_za_testove";
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
	StripeWebhookService webhook;

	private String amra;
	private String hana;
	private UUID teretana;

	@BeforeEach
	void teretanaSaClanicom() throws Exception {
		amra = token("amra.webhook@example.com", "Amra");
		hana = token("hana.webhook@example.com", "Hana");
		var postojeca = jdbc.queryForList("select id from organizations where slug = 'teretana-webhook'", UUID.class);
		teretana = postojeca.isEmpty() ? novaOrganizacija() : postojeca.getFirst();
		jdbc.update("insert into organization_members values (?, ?, 'CLIENT', now()) on conflict do nothing", teretana,
				users.findByEmail("hana.webhook@example.com").orElseThrow().getId());
		stripe.neRadi(false);
	}

	@Test
	void uplataPotvrdjujeRezervacijuIPamtiPaymentIntent() throws Exception {
		Placanje placanje = hanaOtvaraPlacanje();
		String pi = "pi_" + placanje.rezervacija().toString().substring(0, 8);

		posalji(potpisan(dogadjaj("evt_1_" + pi, StripeDogadjaj.PLACENO, placanje.sesija(), pi, "paid")))
				.andExpect(status().isOk());

		assertThat(statusRezervacije(placanje)).isEqualTo("CONFIRMED");
		assertThat(jdbc.queryForMap("select status, stripe_payment_intent_id from payments where stripe_session_id = ?",
				placanje.sesija())).containsEntry("status", "PAID").containsEntry("stripe_payment_intent_id", pi);
		// U historiji je potvrdila Hana, svojom uplatom, i dobija obavijest kao i inače
		assertThat(jdbc.queryForList("select actor_id from booking_events where booking_id = ? and to_status = "
				+ "'CONFIRMED'", UUID.class, placanje.rezervacija()))
				.containsExactly(users.findByEmail("hana.webhook@example.com").orElseThrow().getId());
		assertThat(jdbc.queryForObject("select count(*) from notifications where booking_id = ? and type = "
				+ "'BOOKING_CONFIRMED'", Long.class, placanje.rezervacija())).isEqualTo(1);
	}

	// Stripe ponovi događaj kad ne dobije naš 200 (npr. odgovor se izgubio). Drugi put se ništa ne
	// mijenja: nema druge potvrde ni obavijesti, i nema povrata novca za potvrđenu rezervaciju.
	@Test
	void istiDogadjajDvaputNePotvrdjujeDvaputNiVracaNovac() throws Exception {
		Placanje placanje = hanaOtvaraPlacanje();
		String pi = "pi_" + placanje.rezervacija().toString().substring(0, 8);
		String tijelo = dogadjaj("evt_2_" + pi, StripeDogadjaj.PLACENO, placanje.sesija(), pi, "paid");

		posalji(potpisan(tijelo)).andExpect(status().isOk());
		posalji(potpisan(tijelo)).andExpect(status().isOk());

		assertThat(statusRezervacije(placanje)).isEqualTo("CONFIRMED");
		assertThat(jdbc.queryForObject("select count(*) from booking_events where booking_id = ? and to_status = "
				+ "'CONFIRMED'", Long.class, placanje.rezervacija())).isEqualTo(1);
		assertThat(stripe.povratiZa(pi)).isEmpty();
	}

	// Pet istih dostava u istom trenutku. Lock rezervacije ih poreda, pa tačno jedna potvrdi, a ostale
	// nađu PAID. Servis se zove direktno, jer je ovdje predmet lock, a potpis dokazuju drugi testovi.
	@Test
	void istovremeneIsteDostaveObradjujeTacnoJedna() throws Exception {
		Placanje placanje = hanaOtvaraPlacanje();
		String pi = "pi_" + placanje.rezervacija().toString().substring(0, 8);
		Potpisan dostava = potpisan(dogadjaj("evt_3_" + pi, StripeDogadjaj.PLACENO, placanje.sesija(), pi, "paid"));

		CountDownLatch start = new CountDownLatch(1);
		List<Future<?>> odgovori = new ArrayList<>();
		try (ExecutorService niti = Executors.newFixedThreadPool(5)) {
			for (int i = 0; i < 5; i++) {
				odgovori.add(niti.submit(() -> {
					start.await();
					webhook.primi(dostava.tijelo(), dostava.potpis());
					return null;
				}));
			}
			start.countDown();
		}
		for (Future<?> odgovor : odgovori) {
			odgovor.get();
		}

		assertThat(jdbc.queryForObject("select count(*) from booking_events where booking_id = ? and to_status = "
				+ "'CONFIRMED'", Long.class, placanje.rezervacija())).isEqualTo(1);
		assertThat(stripe.povratiZa(pi)).isEmpty();
	}

	// Neko zna adresu i pošalje "plaćeno" za tuđu sesiju, ili izmijeni pravi Stripeov zahtjev usput
	@Test
	void izmijenjenoTijeloIZahtjevBezPotpisaSeOdbijajuINistaSeNeMijenja() throws Exception {
		Placanje placanje = hanaOtvaraPlacanje();
		String pi = "pi_" + placanje.rezervacija().toString().substring(0, 8);
		Potpisan pravi = potpisan(dogadjaj("evt_4_" + pi, StripeDogadjaj.PLACENO, placanje.sesija(), pi, "unpaid"));
		String izmijenjeno = pravi.tijelo().replace("\"unpaid\"", "\"paid\"");

		posalji(new Potpisan(izmijenjeno, pravi.potpis()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Neispravan webhook"));
		posalji(new Potpisan(izmijenjeno, null)).andExpect(status().isBadRequest());

		assertThat(statusRezervacije(placanje)).isEqualTo("PENDING");
		assertThat(statusUplate(placanje)).isEqualTo("OPEN");
	}

	// Ispravan potpis od prije šest minuta: neko je snimio pravi zahtjev i šalje ga ponovo
	@Test
	void potpisStarijiOdPetMinutaSeOdbija() throws Exception {
		Placanje placanje = hanaOtvaraPlacanje();
		String pi = "pi_" + placanje.rezervacija().toString().substring(0, 8);
		String tijelo = dogadjaj("evt_5_" + pi, StripeDogadjaj.PLACENO, placanje.sesija(), pi, "paid");

		posalji(potpisan(tijelo, Instant.now().minusSeconds(360))).andExpect(status().isBadRequest());

		assertThat(statusRezervacije(placanje)).isEqualTo("PENDING");
	}

	// Posao je istekao rezervaciju prije nego što je uplata stigla, pa je termin možda već tuđi
	@Test
	void uplataZaIstekluRezervacijuSeVracaIRezervacijaOstajeIstekla() throws Exception {
		Placanje placanje = hanaOtvaraPlacanje();
		String pi = "pi_" + placanje.rezervacija().toString().substring(0, 8);
		jdbc.update("update bookings set status = 'EXPIRED' where id = ?", placanje.rezervacija());

		posalji(potpisan(dogadjaj("evt_6_" + pi, StripeDogadjaj.PLACENO, placanje.sesija(), pi, "paid")))
				.andExpect(status().isOk());

		assertThat(stripe.povratiZa(pi)).hasSize(1);
		assertThat(statusUplate(placanje)).isEqualTo("REFUNDED");
		assertThat(statusRezervacije(placanje)).isEqualTo("EXPIRED");
	}

	// Ako Stripe ne prihvati povrat, ne smije ostati zapisano da je novac vraćen. Na 503 Stripe šalje
	// događaj ponovo, pa se povrat pokuša opet.
	@Test
	void kadPovratNeProdjeUplataOstajeOtvorenaAStripeDobija503() throws Exception {
		Placanje placanje = hanaOtvaraPlacanje();
		String pi = "pi_" + placanje.rezervacija().toString().substring(0, 8);
		jdbc.update("update bookings set status = 'EXPIRED' where id = ?", placanje.rezervacija());
		stripe.neRadi(true);

		posalji(potpisan(dogadjaj("evt_7_" + pi, StripeDogadjaj.PLACENO, placanje.sesija(), pi, "paid")))
				.andExpect(status().isServiceUnavailable());

		assertThat(statusUplate(placanje)).isEqualTo("OPEN");
	}

	@Test
	void istekSesijeOznacavaUplatuKaoIstekluARezervacijuNeDira() throws Exception {
		Placanje placanje = hanaOtvaraPlacanje();

		posalji(potpisan(dogadjaj("evt_8_" + placanje.rezervacija(), StripeDogadjaj.ISTEKLO, placanje.sesija(), null,
				"unpaid"))).andExpect(status().isOk());

		assertThat(statusUplate(placanje)).isEqualTo("EXPIRED");
		assertThat(statusRezervacije(placanje)).isEqualTo("PENDING");
	}

	// Lokalni backend i Render dijele isti Stripe sandbox, pa stižu i sesije koje ovdje ne postoje.
	// 200, da ih Stripe ne šalje ponovo danima.
	@Test
	void dogadjajZaNepoznatuSesijuSePrihvataIPreskace() throws Exception {
		posalji(potpisan(dogadjaj("evt_9_" + UUID.randomUUID(), StripeDogadjaj.PLACENO, "cs_test_nepoznata", "pi_x",
				"paid"))).andExpect(status().isOk());
	}

	private record Placanje(UUID rezervacija, String sesija) {
	}

	private record Potpisan(String tijelo, String potpis) {
	}

	// Sala od 15 KM, Hanina rezervacija i otvorena uplata kroz pravi tok (lažni Stripe daje sesiju)
	private Placanje hanaOtvaraPlacanje() throws Exception {
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
				.header("Authorization", "Bearer " + hana)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"startTime": "2035-11-19T09:00:00+01:00"}
						""")));
		mockMvc.perform(post("/api/bookings/{id}/pay", rezervacija).header("Authorization", "Bearer " + hana))
				.andExpect(status().isOk());
		return new Placanje(rezervacija, jdbc.queryForObject(
				"select stripe_session_id from payments where booking_id = ?", String.class, rezervacija));
	}

	// Samo polja koja čitamo, u obliku u kojem ih Stripe šalje
	private static String dogadjaj(String id, String tip, String sesija, String paymentIntent, String placeno) {
		return """
				{"id": "%s", "object": "event", "type": "%s", "api_version": "2025-01-01", "created": 1,
				 "data": {"object": {"id": "%s", "object": "checkout.session", "payment_intent": %s,
				  "payment_status": "%s", "amount_total": 1500, "currency": "bam"}}}
				""".formatted(id, tip, sesija, paymentIntent == null ? "null" : "\"" + paymentIntent + "\"", placeno);
	}

	private static Potpisan potpisan(String tijelo) throws Exception {
		return potpisan(tijelo, Instant.now());
	}

	// Kao Stripe: HMAC-SHA256 nad "vrijeme.tijelo" tajnom webhooka
	private static Potpisan potpisan(String tijelo, Instant kad) throws Exception {
		long t = kad.getEpochSecond();
		return new Potpisan(tijelo, "t=" + t + ",v1=" + Webhook.Util.computeHmacSha256(TAJNA, t + "." + tijelo));
	}

	private ResultActions posalji(Potpisan dostava) throws Exception {
		var zahtjev = post("/api/stripe/webhook").contentType(MediaType.APPLICATION_JSON).content(dostava.tijelo());
		if (dostava.potpis() != null) {
			zahtjev.header("Stripe-Signature", dostava.potpis());
		}
		return mockMvc.perform(zahtjev);
	}

	private String statusRezervacije(Placanje placanje) {
		return jdbc.queryForObject("select status from bookings where id = ?", String.class, placanje.rezervacija());
	}

	private String statusUplate(Placanje placanje) {
		return jdbc.queryForObject("select status from payments where stripe_session_id = ?", String.class,
				placanje.sesija());
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
						{"name": "Teretana Ilidža", "slug": "teretana-webhook", "timezone": "Europe/Sarajevo"}
						""")));
	}

}
