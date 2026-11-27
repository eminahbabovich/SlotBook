package ba.slotbook.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
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

import com.jayway.jsonpath.JsonPath;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.availability.AvailabilityInterval;
import ba.slotbook.availability.AvailabilityService;
import ba.slotbook.organization.OrganizationService;
import ba.slotbook.resource.ResourceService;
import ba.slotbook.user.UserService;

// Dugme Potvrdi u chatu. Prijedlog je napravio alat, a rezervacija nastaje tek ovdje, kroz isti
// BookingService kao u kalendaru. Sto 4 radi svaki dan od 08 do 16, termini su po sat.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ChatProposalApiTest {

	private static final String LOZINKA = "potvrda-u-chatu-8";
	// 26.3.2035. je već ljetno vrijeme, pa je 10:00 u Sarajevu 08:00 UTC
	private static final Instant DESET = Instant.parse("2035-03-26T08:00:00Z");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	UserService userService;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	ResourceService resourceService;

	@Autowired
	AvailabilityService availabilityService;

	@Autowired
	ChatSessionService sesije;

	@Autowired
	ChatProposalService prijedlozi;

	private final String sufiks = UUID.randomUUID().toString().substring(0, 6);
	private UUID amila;
	private UUID lejla;
	private UUID emir;
	private UUID citaonica;
	private UUID sto4;
	private UUID lejlinRazgovor;

	@BeforeEach
	void citaonica() {
		amila = korisnik("amila", "Amila Begić");
		lejla = korisnik("lejla", "Lejla Šehić");
		emir = korisnik("emir", "Emir Đulić");
		citaonica = organizationService.create(amila, "Čitaonica FIT", "citaonica-prijedlog-" + sufiks, "Europe/Sarajevo")
				.id();
		clan(lejla);
		clan(emir);
		sto4 = resourceService.create(citaonica, amila, "Sto 4, tihi dio", null, 60, true, 0, 1, 0).id();
		availabilityService.replace(sto4, Arrays.stream(DayOfWeek.values())
				.map(d -> new AvailabilityInterval(d, LocalTime.of(8, 0), LocalTime.of(16, 0))).toList());
		lejlinRazgovor = sesije.open(citaonica, lejla).id();
	}

	@Test
	void bezPotvrdeNemaRezervacijeAPotvrdaJePraviKrozBookingService() throws Exception {
		UUID prijedlog = prijedlozi.predloziRezervaciju(lejlinRazgovor, sto4, DESET);
		assertThat(brojRezervacija()).isZero();

		String tijelo = potvrdi(prijedlog, "lejla")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.action").value("BOOK"))
				.andExpect(jsonPath("$.bookingStatus").value("CONFIRMED"))
				.andReturn().getResponse().getContentAsString();

		assertThat(jdbc.queryForObject("select client_id from bookings where id = ?::uuid", UUID.class,
				(Object) JsonPath.read(tijelo, "$.bookingId"))).isEqualTo(lejla);
		// Ključ iz prijedloga je i ključ rezervacije, pa BookingService prepozna ponovljen zahtjev
		assertThat(jdbc.queryForObject("select b.idempotency_key = p.idempotency_key from bookings b, chat_proposals p "
				+ "where b.resource_id = ? and p.id = ?", Boolean.class, sto4, prijedlog)).isTrue();
	}

	// Dupli klik ili dva taba: obje potvrde dobiju istu rezervaciju, a u bazi je jedna
	@Test
	void dvijeIstovremenePotvrdeIstogPrijedlogaDajuJednuRezervaciju() throws Exception {
		UUID prijedlog = prijedlozi.predloziRezervaciju(lejlinRazgovor, sto4, DESET);
		String token = prijava("lejla");

		List<String> odgovori = istovremeno(2, i -> mockMvc.perform(post("/api/chat/proposals/{id}/confirm", prijedlog)
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());

		assertThat(odgovori).extracting(o -> (String) JsonPath.read(o, "$.bookingId")).containsOnly(
				JsonPath.<String>read(odgovori.getFirst(), "$.bookingId"));
		assertThat(brojRezervacija()).isEqualTo(1);
	}

	// Dva razgovora predlože isti termin. Prvi dobije termin, drugi isti 409 kao u kalendaru.
	@Test
	void dvaPrijedlogaZaIstiTerminDajuJednuRezervacijuIJedan409() throws Exception {
		UUID lejlin = prijedlozi.predloziRezervaciju(lejlinRazgovor, sto4, DESET);
		UUID emirov = prijedlozi.predloziRezervaciju(sesije.open(citaonica, emir).id(), sto4, DESET);

		potvrdi(lejlin, "lejla").andExpect(status().isOk());
		potvrdi(emirov, "emir").andExpect(status().isConflict());

		assertThat(brojRezervacija()).isEqualTo(1);
	}

	@Test
	void istekaoPrijedlogSeNeMozePotvrditi() throws Exception {
		UUID prijedlog = prijedlozi.predloziRezervaciju(lejlinRazgovor, sto4, DESET);
		jdbc.update("update chat_proposals set expires_at = now() - interval '1 second' where id = ?", prijedlog);

		potvrdi(prijedlog, "lejla")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("Prijedlog je istekao. Pitaj asistenta ponovo za ovaj termin."));
		assertThat(brojRezervacija()).isZero();
	}

	@Test
	void odbijenPrijedlogSeNeMozePotvrditiAOdbijanjeSeSmijePonoviti() throws Exception {
		UUID prijedlog = prijedlozi.predloziRezervaciju(lejlinRazgovor, sto4, DESET);
		String token = prijava("lejla");

		mockMvc.perform(post("/api/chat/proposals/{id}/dismiss", prijedlog).header("Authorization", "Bearer " + token))
				.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/chat/proposals/{id}/dismiss", prijedlog).header("Authorization", "Bearer " + token))
				.andExpect(status().isNoContent());
		potvrdi(prijedlog, "lejla").andExpect(status().isConflict());

		assertThat(brojRezervacija()).isZero();
	}

	@Test
	void tudjiPrijedlogNePotvrdjujeNiAdminOrganizacije() throws Exception {
		UUID prijedlog = prijedlozi.predloziRezervaciju(lejlinRazgovor, sto4, DESET);

		potvrdi(prijedlog, "amila").andExpect(status().isForbidden());
		mockMvc.perform(post("/api/chat/proposals/{id}/dismiss", prijedlog)
				.header("Authorization", "Bearer " + prijava("amila")))
				.andExpect(status().isForbidden());

		assertThat(brojRezervacija()).isZero();
		assertThat(jdbc.queryForObject("select dismissed_at is null from chat_proposals where id = ?", Boolean.class,
				prijedlog)).isTrue();
	}

	// Admin je u međuvremenu stavio cijenu. Chat ne pravi plaćene rezervacije ni na potvrdi.
	@Test
	void resursKojiJePostaoPlacenSeNaPotvrdiOdbija() throws Exception {
		UUID prijedlog = prijedlozi.predloziRezervaciju(lejlinRazgovor, sto4, DESET);
		jdbc.update("update resources set price_cents = 500 where id = ?", sto4);

		potvrdi(prijedlog, "lejla").andExpect(status().isConflict());

		assertThat(brojRezervacija()).isZero();
	}

	@Test
	void otkazivanjeIdeKrozPrijedlogIDrugaPotvrdaNijeGreska() throws Exception {
		UUID rezervacija = UUID.randomUUID();
		jdbc.update("insert into bookings (id, resource_id, client_id, start_time, end_time, status, created_at) "
				+ "values (?, ?, ?, ?, ?, 'CONFIRMED', now())", rezervacija, sto4, lejla, Timestamp.from(DESET),
				Timestamp.from(DESET.plusSeconds(3600)));
		UUID prijedlog = prijedlozi.predloziOtkazivanje(lejlinRazgovor, rezervacija);
		assertThat(statusRezervacije(rezervacija)).isEqualTo("CONFIRMED");

		potvrdi(prijedlog, "lejla").andExpect(status().isOk()).andExpect(jsonPath("$.bookingStatus").value("CANCELLED"));
		potvrdi(prijedlog, "lejla").andExpect(status().isOk()).andExpect(jsonPath("$.bookingStatus").value("CANCELLED"));

		assertThat(statusRezervacije(rezervacija)).isEqualTo("CANCELLED");
	}

	private org.springframework.test.web.servlet.ResultActions potvrdi(UUID prijedlog, String ime) throws Exception {
		return mockMvc.perform(post("/api/chat/proposals/{id}/confirm", prijedlog)
				.header("Authorization", "Bearer " + prijava(ime)));
	}

	private interface Zahtjev {
		String posalji(int i) throws Exception;
	}

	private static List<String> istovremeno(int koliko, Zahtjev zahtjev) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		List<Future<String>> rezultati = new ArrayList<>();
		try (ExecutorService izvrsilac = Executors.newFixedThreadPool(koliko)) {
			for (int i = 0; i < koliko; i++) {
				int broj = i;
				rezultati.add(izvrsilac.submit(() -> {
					start.await();
					return zahtjev.posalji(broj);
				}));
			}
			start.countDown();
			List<String> odgovori = new ArrayList<>();
			for (Future<String> rezultat : rezultati) {
				odgovori.add(rezultat.get());
			}
			return odgovori;
		}
	}

	private long brojRezervacija() {
		return jdbc.queryForObject("select count(*) from bookings where resource_id = ?", Long.class, sto4);
	}

	private String statusRezervacije(UUID id) {
		return jdbc.queryForObject("select status from bookings where id = ?", String.class, id);
	}

	private void clan(UUID korisnik) {
		jdbc.update("insert into organization_members values (?, ?, 'CLIENT', now())", citaonica, korisnik);
	}

	private UUID korisnik(String ime, String imeIPrezime) {
		return userService.register(email(ime), LOZINKA, imeIPrezime).id();
	}

	private String prijava(String ime) throws Exception {
		jdbc.update("update users set email_verified_at = now() where email = ?", email(ime));
		String tijelo = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "%s"}
				""".formatted(email(ime), LOZINKA))).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
		return JsonPath.read(tijelo, "$.accessToken");
	}

	private String email(String ime) {
		return ime + ".prijedlog." + sufiks + "@example.com";
	}

}
