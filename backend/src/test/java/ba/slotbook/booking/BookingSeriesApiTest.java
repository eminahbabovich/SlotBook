package ba.slotbook.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class BookingSeriesApiTest {

	private static final String LOZINKA = "konsultacije-utorkom";
	private static final String ZONA = "Europe/Sarajevo";
	// Ponedjeljak prije prelaska na ljetno vrijeme (Sarajevo prelazi u nedjelju 30.3.2036)
	private static final String PRVI_PONEDJELJAK = "2036-03-17T09:00:00+01:00";

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
	BookingService bookingService;

	private final String sufiks = UUID.randomUUID().toString().substring(0, 6);
	private UUID lejla;
	private UUID amar;
	private UUID kabinet;
	private String lejlinToken;

	@BeforeEach
	void konsultacije() throws Exception {
		UUID profesorica = korisnik("profesorica");
		lejla = korisnik("lejla");
		amar = korisnik("amar");
		UUID fakultet = organizationService.create(profesorica, "Konsultacije ETF", "etf-serija-" + sufiks, ZONA).id();
		clan(fakultet, lejla);
		clan(fakultet, amar);
		kabinet = resourceService.create(fakultet, profesorica, "Kabinet 2-14", null, 60, true, 0).id();
		availabilityService.replace(kabinet,
				List.of(new AvailabilityInterval(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(16, 0))));
		lejlinToken = prijava("lejla");
	}

	@Test
	void serijaPrekoLjetnogVremenaOstajeU9PoSarajevuAUUtcuSePomjeriZaSat() throws Exception {
		String odgovor = serija(PRVI_PONEDJELJAK, 4, null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.bookings[*].startTime", contains("2036-03-17T08:00:00Z", "2036-03-24T08:00:00Z",
						"2036-03-31T07:00:00Z", "2036-04-07T07:00:00Z")))
				.andExpect(jsonPath("$.bookings[*].status", everyItem(is("CONFIRMED"))))
				.andReturn().getResponse().getContentAsString();

		UUID serija = UUID.fromString(JsonPath.read(odgovor, "$.seriesId"));
		assertThat(broj("select count(*) from bookings where series_id = ? and client_id = ?", serija, lejla))
				.isEqualTo(4);
		// Svaka sedmica ima svoj zapis nastanka, kao obična rezervacija
		assertThat(broj("select count(*) from booking_events e join bookings b on b.id = e.booking_id "
				+ "where b.series_id = ? and e.from_status is null", serija)).isEqualTo(4);
	}

	@Test
	void jednaZauzetaSedmicaObaraCijeluSerijuA409KazeKoja() throws Exception {
		bookingService.create(kabinet, amar, Instant.parse("2036-03-24T08:00:00Z"), null);

		serija(PRVI_PONEDJELJAK, 3, null)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.title").value("Neki termini iz serije nisu slobodni"))
				.andExpect(jsonPath("$.zauzetiTermini", contains("2036-03-24T08:00:00Z")));

		assertThat(broj("select count(*) from bookings where client_id = ?", lejla)).isZero();
	}

	// Sedmica u kojoj resurs ne radi (ovdje 16:00, poslije radnog vremena) je isto "nije slobodna"
	@Test
	void terminKojiSeNeNudiJeUListiKaoIZauzet() throws Exception {
		serija("2036-03-17T16:00:00+01:00", 2, null)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.zauzetiTermini", contains("2036-03-17T15:00:00Z", "2036-03-24T15:00:00Z")));
	}

	@Test
	void ponovljenZahtjevSIstimKljucemVracaIstuSerijuBezNovihRezervacija() throws Exception {
		String kljuc = UUID.randomUUID().toString();
		String prvi = serija(PRVI_PONEDJELJAK, 2, kljuc).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString();

		String drugi = serija(PRVI_PONEDJELJAK, 2, kljuc).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString();

		assertThat(JsonPath.<String>read(drugi, "$.seriesId")).isEqualTo(JsonPath.read(prvi, "$.seriesId"));
		assertThat(broj("select count(*) from bookings where client_id = ?", lejla)).isEqualTo(2);
		// Isti ključ za drugi broj sedmica je drugi zahtjev
		serija(PRVI_PONEDJELJAK, 3, kljuc).andExpect(status().isUnprocessableEntity());
	}

	@Test
	void viseOd12SedmicaJe400SPorukomZaPolje() throws Exception {
		serija(PRVI_PONEDJELJAK, 13, null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.greske[0].polje").value("weeks"))
				.andExpect(jsonPath("$.greske[0].poruka").value("Serija ima najviše 12 sedmica."));
	}

	private ResultActions serija(String pocetak, int sedmica, String kljuc) throws Exception {
		var zahtjev = post("/api/resources/{id}/bookings/series", kabinet)
				.header("Authorization", "Bearer " + lejlinToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"startTime": "%s", "weeks": %d}
						""".formatted(pocetak, sedmica));
		if (kljuc != null) {
			zahtjev.header("Idempotency-Key", kljuc);
		}
		return mockMvc.perform(zahtjev);
	}

	private long broj(String sql, Object... parametri) {
		return jdbc.queryForObject(sql, Long.class, parametri);
	}

	private UUID korisnik(String ime) {
		return userService.register(email(ime), LOZINKA, ime).id();
	}

	private void clan(UUID organizacija, UUID korisnik) {
		jdbc.update("insert into organization_members values (?, ?, 'CLIENT', now())", organizacija, korisnik);
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
		return ime + ".serija." + sufiks + "@example.com";
	}

}
