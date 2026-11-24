package ba.slotbook.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
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

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.user.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PaymentApiTest {

	private static final String LOZINKA = "bascarsija-sebilj-1753";
	private static final String PONEDJELJAK_09 = "2035-11-19T09:00:00+01:00";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	LazniStripeCheckout stripe;

	@Autowired
	PaymentService paymentService;

	private String amra;
	private String hana;
	private String lamija;
	private UUID teretana;

	// Amra je admin teretane, Hana i Lamija članice (CLIENT). Svaki test pravi svoju salu.
	@BeforeEach
	void teretanaSaDvijeClanice() throws Exception {
		amra = token("amra.uplate@example.com", "Amra");
		hana = token("hana.uplate@example.com", "Hana");
		lamija = token("lamija.uplate@example.com", "Lamija");
		var postojeca = jdbc.queryForList("select id from organizations where slug = 'teretana-uplate'", UUID.class);
		teretana = postojeca.isEmpty() ? novaOrganizacija() : postojeca.getFirst();
		for (String email : new String[] { "hana.uplate@example.com", "lamija.uplate@example.com" }) {
			jdbc.update("insert into organization_members values (?, ?, 'CLIENT', now()) on conflict do nothing",
					teretana, users.findByEmail(email).orElseThrow().getId());
		}
		stripe.neRadi(false);
		stripe.kasni(Duration.ZERO);
	}

	@Test
	void clanicaDobijaAdresuCheckoutaAUplataJeOtvorenaSIznosomRezervacije() throws Exception {
		UUID rezervacija = rezervacija(hana, sala(1500));

		plati(hana, rezervacija)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.url", startsWith("https://checkout.stripe.com/")));

		assertThat(jdbc.queryForMap("select amount_cents, currency, status from payments where booking_id = ?",
				rezervacija)).containsEntry("amount_cents", 1500).containsEntry("currency", "bam")
				.containsEntry("status", "OPEN");
		// Naziv nosi vrijeme po Sarajevu (09:00), ne UTC (08:00), jer ga klijent čita na Stripeovoj stranici
		assertThat(stripe.poziviZa(rezervacija)).singleElement().satisfies(poziv -> {
			assertThat(poziv.iznosFeninga()).isEqualTo(1500);
			assertThat(poziv.naziv()).endsWith("19.11.2035. u 09:00");
			assertThat(poziv.povratakUspjeh()).endsWith("/rezervacije?uplata=uspjeh");
		});
	}

	@Test
	void drugiKlikVracaIstuSesijuIStripeSeZoveSamoJednom() throws Exception {
		UUID rezervacija = rezervacija(hana, sala(1500));

		String prva = url(plati(hana, rezervacija));
		String druga = url(plati(hana, rezervacija));

		assertThat(druga).isEqualTo(prva);
		assertThat(stripe.poziviZa(rezervacija)).hasSize(1);
	}

	// Pet klikova u istom trenutku, a Stripe odgovara za 300 ms. Bez locka na rezervaciji svih pet ne
	// nađe otvorenu uplatu i zove Stripe, a indeks payments_jedna_otvorena četiri odbije s greškom.
	// Zove se servis, ne HTTP, jer je ovdje predmet lock, a pristup dokazuju ostali testovi.
	@Test
	void istovremeniKlikoviCekajuPrviIDobijajuNjegovuSesiju() throws Exception {
		UUID rezervacija = rezervacija(hana, sala(1500));
		stripe.kasni(Duration.ofMillis(300));

		CountDownLatch start = new CountDownLatch(1);
		List<Future<String>> odgovori = new ArrayList<>();
		try (ExecutorService niti = Executors.newFixedThreadPool(5)) {
			for (int i = 0; i < 5; i++) {
				odgovori.add(niti.submit(() -> {
					start.await();
					return paymentService.checkout(rezervacija).url();
				}));
			}
			start.countDown();
		}
		List<String> adrese = new ArrayList<>();
		for (Future<String> odgovor : odgovori) {
			adrese.add(odgovor.get());
		}

		assertThat(adrese).hasSize(5).containsOnly(adrese.getFirst());
		assertThat(stripe.poziviZa(rezervacija)).hasSize(1);
	}

	// Lamija je članica iste teretane, Amra admin. Nijedna nije klijent ove rezervacije.
	@Test
	void tudjuRezervacijuNePlacaNiDrugaClanicaNiAdmin() throws Exception {
		UUID rezervacija = rezervacija(hana, sala(1500));

		plati(lamija, rezervacija).andExpect(status().isForbidden());
		plati(amra, rezervacija).andExpect(status().isForbidden());

		assertThat(brojUplata(rezervacija)).isZero();
		assertThat(stripe.poziviZa(rezervacija)).isEmpty();
	}

	@Test
	void besplatnaRezervacijaSeNePlaca() throws Exception {
		UUID rezervacija = rezervacija(hana, sala(0));

		plati(hana, rezervacija)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Rezervacija ne čeka uplatu"));
		assertThat(stripe.poziviZa(rezervacija)).isEmpty();
	}

	// Rok je prošao, a posao za istek još nije prošao (Render spava). Status je i dalje PENDING.
	@Test
	void rezervacijaKojojJeProsaoRokSeNePlacaIakoJeJosPending() throws Exception {
		UUID rezervacija = rezervacija(hana, sala(1500));
		jdbc.update("update bookings set expires_at = now() - interval '1 minute' where id = ?", rezervacija);

		plati(hana, rezervacija).andExpect(status().isConflict());

		assertThat(stripe.poziviZa(rezervacija)).isEmpty();
		assertThat(brojUplata(rezervacija)).isZero();
	}

	@Test
	void kadStripeNeRadiKlijentDobija503ARezervacijaOstajeBezUplate() throws Exception {
		UUID rezervacija = rezervacija(hana, sala(1500));
		stripe.neRadi(true);

		plati(hana, rezervacija)
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.title").value("Plaćanje nije dostupno"));

		assertThat(brojUplata(rezervacija)).isZero();
		assertThat(jdbc.queryForObject("select status from bookings where id = ?", String.class, rezervacija))
				.isEqualTo("PENDING");
	}

	private long brojUplata(UUID rezervacija) {
		return jdbc.queryForObject("select count(*) from payments where booking_id = ?", Long.class, rezervacija);
	}

	private ResultActions plati(String token, UUID rezervacija) throws Exception {
		return mockMvc.perform(post("/api/bookings/{id}/pay", rezervacija).header("Authorization", "Bearer " + token));
	}

	private static String url(ResultActions odgovor) throws Exception {
		return JsonPath.read(odgovor.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
				"$.url");
	}

	// Sala sa satnim terminima ponedjeljkom od 08 do 12, s automatskom potvrdom, da se vidi da cijena
	// ipak traži uplatu
	private UUID sala(int feninga) throws Exception {
		UUID sala = idIz(mockMvc.perform(post("/api/organizations/{id}/resources", teretana)
				.header("Authorization", "Bearer " + amra)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Sala %s", "slotMinutes": 60, "autoConfirm": true, "priceCents": %d}
						""".formatted(UUID.randomUUID().toString().substring(0, 8), feninga))));
		mockMvc.perform(put("/api/resources/{id}/availability", sala)
				.header("Authorization", "Bearer " + amra)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"rules": [{"dayOfWeek": "MONDAY", "start": "08:00", "end": "12:00"}]}
						"""))
				.andExpect(status().isOk());
		return sala;
	}

	private UUID rezervacija(String token, UUID sala) throws Exception {
		return idIz(mockMvc.perform(post("/api/resources/{id}/bookings", sala)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"startTime": "%s"}
						""".formatted(PONEDJELJAK_09))));
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
						{"name": "Teretana Grbavica", "slug": "teretana-uplate", "timezone": "Europe/Sarajevo"}
						""")));
	}

}
