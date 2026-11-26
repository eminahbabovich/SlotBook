package ba.slotbook.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
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
import ba.slotbook.availability.AvailabilityInterval;
import ba.slotbook.availability.AvailabilityService;
import ba.slotbook.organization.OrganizationService;
import ba.slotbook.resource.ResourceService;
import ba.slotbook.user.UserService;

// Čitaonica s jednim stolom koji radi svaki dan od 08 do 16. Pravila se upisuju u bazu po testu,
// a termini su relativni od danas, jer pravila računaju od pravog trenutka.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class BookingRulesApiTest {

	private static final String LOZINKA = "sto-sedam-uz-prozor";
	private static final ZoneId ZONA = ZoneId.of("Europe/Sarajevo");

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

	private final String sufiks = UUID.randomUUID().toString().substring(0, 6);
	private UUID lejla;
	private UUID citaonica;
	private UUID sto;
	private String lejlin;
	private String profesorice;

	@BeforeEach
	void citaonicaSaStolom() throws Exception {
		UUID profesorica = korisnik("profesorica");
		lejla = korisnik("lejla");
		citaonica = organizationService.create(profesorica, "Čitaonica pravila", "citaonica-pravila-" + sufiks,
				ZONA.getId()).id();
		jdbc.update("insert into organization_members values (?, ?, 'CLIENT', now())", citaonica, lejla);
		sto = resourceService.create(citaonica, profesorica, "Sto 7, uz prozor", null, 60, true, 0, 1, 0).id();
		availabilityService.replace(sto, Arrays.stream(DayOfWeek.values())
				.map(d -> new AvailabilityInterval(d, LocalTime.of(8, 0), LocalTime.of(16, 0))).toList());
		lejlin = prijava("lejla");
		profesorice = prijava("profesorica");
	}

	@Test
	void najviseDvijeBuduceRezervacijeTrecaDobija422SPorukomAAdminNijeOgranicen() throws Exception {
		jdbc.update("update resources set max_active_per_member = 2 where id = ?", sto);

		rezervisi(lejlin, zaDana(2, 10)).andExpect(status().isCreated());
		rezervisi(lejlin, zaDana(3, 10)).andExpect(status().isCreated());
		rezervisi(lejlin, zaDana(4, 10))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.pravilo").value("MAX_ACTIVE"))
				.andExpect(jsonPath("$.detail").value(
						"Granica budućih rezervacija po članu na ovom resursu je 2, a ti već imaš 2. Otkaži neku ili sačekaj da prođe."));

		for (int dan = 2; dan <= 4; dan++) {
			rezervisi(profesorice, zaDana(dan, 12)).andExpect(status().isCreated());
		}
	}

	// Prošla i otkazana se ne broje: granica je na budućim aktivnim
	@Test
	void otkazanaRezervacijaOslobodiMjestoUGranici() throws Exception {
		jdbc.update("update resources set max_active_per_member = 1 where id = ?", sto);
		String prva = rezervisi(lejlin, zaDana(2, 10)).andReturn().getResponse().getContentAsString();
		rezervisi(lejlin, zaDana(3, 10)).andExpect(status().isUnprocessableContent());

		mockMvc.perform(post("/api/bookings/{id}/cancel", (Object) JsonPath.read(prva, "$.id"))
				.header("Authorization", "Bearer " + lejlin)).andExpect(status().isOk());

		rezervisi(lejlin, zaDana(3, 10)).andExpect(status().isCreated());
	}

	@Test
	void sedamDanaUnaprijedOsmiDanSeOdbija() throws Exception {
		jdbc.update("update resources set max_days_ahead = 7 where id = ?", sto);

		rezervisi(lejlin, zaDana(7, 15)).andExpect(status().isCreated());
		rezervisi(lejlin, zaDana(8, 8))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.pravilo").value("TOO_FAR_AHEAD"))
				.andExpect(jsonPath("$.detail", org.hamcrest.Matchers.startsWith(
						"Ovaj resurs se rezerviše najviše 7 dana unaprijed, zadnji dan je ")));
	}

	@Test
	void rezervacijaKasnijeOdSedmiceUnaprijedSeOdbija() throws Exception {
		// Sedmica unaprijed, da test ne zavisi od sata u kojem se pokrene
		jdbc.update("update resources set min_notice_minutes = 10080 where id = ?", sto);

		rezervisi(lejlin, zaDana(2, 10))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.pravilo").value("TOO_SOON"))
				.andExpect(jsonPath("$.detail").value(
						"Ovaj resurs se rezerviše najkasnije 168 sati prije početka. Izaberi kasniji termin."));
		rezervisi(lejlin, zaDana(8, 10)).andExpect(status().isCreated());
	}

	// Četiri sedmice uz granicu od tri: nijedna ne prođe, ni prve tri
	@Test
	void serijaKojaPrelaziGranicuNeUpiseNijednuSedmicu() throws Exception {
		jdbc.update("update resources set max_active_per_member = 3 where id = ?", sto);

		mockMvc.perform(post("/api/resources/{id}/bookings/series", sto)
				.header("Authorization", "Bearer " + lejlin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"startTime": "%s", "weeks": 4}
						""".formatted(zaDana(2, 10))))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.pravilo").value("MAX_ACTIVE"));

		assertThat(jdbc.queryForObject("select count(*) from bookings where client_id = ?", Long.class, lejla)).isZero();
	}

	// Dva nedolaska u 30 dana daju sedam dana blokade, od zadnjeg nedolaska
	@Test
	void dvaNedolaskaBlokirajuRezervacijuIListuCekanjaDoZadnjegPlusSedamDana() throws Exception {
		nedolasciPravilo();
		Instant zadnji = Instant.now().minus(Duration.ofDays(2)).truncatedTo(ChronoUnit.HOURS);
		nedolazak(Instant.now().minus(Duration.ofDays(5)).truncatedTo(ChronoUnit.HOURS));
		nedolazak(zadnji);

		rezervisi(lejlin, zaDana(2, 10))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.title").value("Rezervacije su blokirane"))
				.andExpect(jsonPath("$.pravilo").value("BLOCKED"))
				.andExpect(jsonPath("$.blokiranDo").value(zadnji.plus(Duration.ofDays(7)).toString()));
		// Na listu čekanja ni za termin koji je zauzela profesorica
		mockMvc.perform(post("/api/resources/{id}/bookings", sto)
				.header("Authorization", "Bearer " + profesorice).contentType(MediaType.APPLICATION_JSON)
				.content("{\"startTime\": \"" + zaDana(3, 10) + "\"}")).andExpect(status().isCreated());
		mockMvc.perform(post("/api/resources/{id}/waitlist", sto)
				.header("Authorization", "Bearer " + lejlin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"startTime\": \"" + zaDana(3, 10) + "\"}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.pravilo").value("BLOCKED"));

		mockMvc.perform(get("/api/me/blocks").header("Authorization", "Bearer " + lejlin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].organizationId").value(citaonica.toString()))
				.andExpect(jsonPath("$[0].noShows").value(2))
				.andExpect(jsonPath("$[0].blockedUntil").value(zadnji.plus(Duration.ofDays(7)).toString()));
	}

	// Stari nedolasci izađu iz perioda od 30 dana, a blokada od zadnjeg je ionako istekla
	@Test
	void nedolasciStarijiOdPeriodaNeBlokiraju() throws Exception {
		nedolasciPravilo();
		nedolazak(Instant.now().minus(Duration.ofDays(40)));
		nedolazak(Instant.now().minus(Duration.ofDays(35)));

		rezervisi(lejlin, zaDana(2, 10)).andExpect(status().isCreated());
		mockMvc.perform(get("/api/me/blocks").header("Authorization", "Bearer " + lejlin))
				.andExpect(jsonPath("$").isEmpty());
	}

	// Pravilo nedolazaka ne važi za onoga ko vodi termine, i on ih može imati (npr. bolovanje)
	@Test
	void adminSNedolascimaNijeBlokiran() throws Exception {
		nedolasciPravilo();
		UUID profesorica = jdbc.queryForObject("select id from users where email = ?", UUID.class, email("profesorica"));
		for (int dan : new int[] { 3, 4 }) {
			jdbc.update("insert into bookings (id, resource_id, client_id, start_time, end_time, status, created_at) "
					+ "values (?, ?, ?, ?, ?, 'NO_SHOW', now())", UUID.randomUUID(), sto, profesorica,
					java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(dan))),
					java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(dan)).plus(Duration.ofHours(1))));
		}

		rezervisi(profesorice, zaDana(2, 10)).andExpect(status().isCreated());
	}

	@Test
	void pravilaResursaIduKrozPutIVracajuSeUOdgovoru() throws Exception {
		String tijelo = """
				{"name": "Sto 7, uz prozor", "slotMinutes": 60, "maxActivePerMember": 3, "maxDaysAhead": 14,
				 "minNoticeMinutes": 60}
				""";
		mockMvc.perform(put("/api/organizations/{id}/resources/{r}", citaonica, sto)
				.header("Authorization", "Bearer " + profesorice).contentType(MediaType.APPLICATION_JSON).content(tijelo))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.maxActivePerMember").value(3))
				.andExpect(jsonPath("$.maxDaysAhead").value(14))
				.andExpect(jsonPath("$.minNoticeMinutes").value(60));

		mockMvc.perform(put("/api/organizations/{id}/resources/{r}", citaonica, sto)
				.header("Authorization", "Bearer " + profesorice).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Sto 7\", \"slotMinutes\": 60, \"maxDaysAhead\": 0}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.greske[0].polje").value("maxDaysAhead"));
		// PUT bez pravila ih ukida, kao i ostala polja koja se ne pošalju
		mockMvc.perform(put("/api/organizations/{id}/resources/{r}", citaonica, sto)
				.header("Authorization", "Bearer " + profesorice).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Sto 7\", \"slotMinutes\": 60}"))
				.andExpect(jsonPath("$.maxActivePerMember").isEmpty());
	}

	@Test
	void praviloNedolazakaTraziSvaTriBrojaIVideGaSvakiClan() throws Exception {
		pravilaNedolazaka("{\"limit\": 2, \"windowDays\": 30}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.greske[0].polje").value("limit"));
		pravilaNedolazaka("{\"limit\": 2, \"windowDays\": 30, \"blockDays\": 7}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.blockDays").value(7));

		String moje = mockMvc.perform(get("/api/me/organizations").header("Authorization", "Bearer " + lejlin))
				.andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<Integer>read(moje, "$[0].noShowRules.limit")).isEqualTo(2);
		assertThat(jdbc.queryForObject("select detail from audit_log where organization_id = ? and action = "
				+ "'ORGANIZATION_RULES_CHANGED'", String.class, citaonica)).isEqualTo("2 u 30 dana -> 7 dana");

		pravilaNedolazaka("{}").andExpect(status().isOk()).andExpect(jsonPath("$.limit").isEmpty());
	}

	private ResultActions pravilaNedolazaka(String tijelo) throws Exception {
		return mockMvc.perform(put("/api/organizations/{id}/no-show-rules", citaonica)
				.header("Authorization", "Bearer " + profesorice).contentType(MediaType.APPLICATION_JSON).content(tijelo));
	}

	private void nedolasciPravilo() {
		jdbc.update("update organizations set no_show_limit = 2, no_show_window_days = 30, no_show_block_days = 7 "
				+ "where id = ?", citaonica);
	}

	private void nedolazak(Instant pocetak) {
		jdbc.update("insert into bookings (id, resource_id, client_id, start_time, end_time, status, created_at) "
				+ "values (?, ?, ?, ?, ?, 'NO_SHOW', now())", UUID.randomUUID(), sto, lejla,
				java.sql.Timestamp.from(pocetak), java.sql.Timestamp.from(pocetak.plus(Duration.ofHours(1))));
	}

	private static String zaDana(int dana, int sat) {
		return LocalDate.now(ZONA).plusDays(dana).atTime(sat, 0).atZone(ZONA).toInstant().toString();
	}

	private ResultActions rezervisi(String token, String pocetak) throws Exception {
		return mockMvc.perform(post("/api/resources/{id}/bookings", sto)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"startTime\": \"" + pocetak + "\"}"));
	}

	private UUID korisnik(String ime) {
		return userService.register(email(ime), LOZINKA, ime).id();
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
		return ime + ".pravila." + sufiks + "@example.com";
	}

}
