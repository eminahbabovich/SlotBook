package ba.slotbook.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.availability.AvailabilityInterval;
import ba.slotbook.availability.AvailabilityService;
import ba.slotbook.booking.BookingService;
import ba.slotbook.organization.OrganizationService;
import ba.slotbook.resource.ResourceService;
import ba.slotbook.user.UserService;

// Čitaonica s dva stola koji rade svaki dan od 08 do 16. Ekran pokazuje cijeli današnji dan, pa
// testovi ne zavise od sata u kojem se pokrenu.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class DoorScreenApiTest {

	private static final String LOZINKA = "ekran-na-vratima-7";
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

	@Autowired
	BookingService bookingService;

	@Autowired
	ScreenStream screenStream;

	private final String sufiks = UUID.randomUUID().toString().substring(0, 6);
	private UUID amila;
	private UUID lejla;
	private UUID citaonica;
	private UUID sto4;
	private UUID sto7;
	private String admin;

	@BeforeEach
	void citaonica() throws Exception {
		amila = korisnik("amila", "Amila Kovačević");
		lejla = korisnik("lejla", "Lejla Hadžić");
		citaonica = organizationService.create(amila, "Čitaonica ekran", "citaonica-ekran-" + sufiks, ZONA.getId()).id();
		jdbc.update("insert into organization_members values (?, ?, 'CLIENT', now())", citaonica, lejla);
		sto4 = sto("Sto 4, tihi dio");
		sto7 = sto("Sto 7, uz prozor");
		admin = prijava("amila");
	}

	@Test
	void ekranBezPrijavePokazujeDanasnjeTermineAliNikogaPoImenu() throws Exception {
		Instant osam = LocalDate.now(ZONA).atTime(8, 0).atZone(ZONA).toInstant();
		jdbc.update("insert into bookings (id, resource_id, client_id, start_time, end_time, status, created_at) "
				+ "values (?, ?, ?, ?, ?, 'CONFIRMED', now())", UUID.randomUUID(), sto4, lejla, Timestamp.from(osam),
				Timestamp.from(osam.plusSeconds(3600)));
		String token = token(napravi(null).andExpect(status().isCreated()));

		String tijelo = mockMvc.perform(get("/api/screens/{token}", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.organizationName").value("Čitaonica ekran"))
				.andExpect(jsonPath("$.date").value(LocalDate.now(ZONA).toString()))
				.andExpect(jsonPath("$.inviteUrl").isEmpty())
				.andExpect(jsonPath("$.resources[*].name", contains("Sto 4, tihi dio", "Sto 7, uz prozor")))
				// Cijeli dan od 08 do 16, i termini koji su već prošli
				.andExpect(jsonPath("$.resources[0].slots", hasSize(8)))
				.andExpect(jsonPath("$.resources[0].slots[0].start").value(osam.toString()))
				.andExpect(jsonPath("$.resources[0].slots[0].seatsLeft").value(0))
				.andExpect(jsonPath("$.resources[0].slots[1].seatsLeft").value(1))
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		assertThat(tijelo).doesNotContain("Lejla", "Hadžić", "lejla.", lejla.toString(), "CONFIRMED");
	}

	@Test
	void pozivniLinkKojiRadiIdeNaEkranZaQrKod() throws Exception {
		String token = token(napravi(null));
		String link = mockMvc.perform(post("/api/organizations/{id}/invite-links", citaonica)
				.header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"maxUses\": 40, \"days\": 30}")).andReturn().getResponse().getContentAsString();

		mockMvc.perform(get("/api/screens/{token}", token))
				.andExpect(jsonPath("$.inviteUrl").value((Object) JsonPath.read(link, "$.url")));
	}

	@Test
	void noviEkranZaIstoMjestoGasiStariAEkranResursaVidiSamoTajResurs() throws Exception {
		String stari = token(napravi(null));
		String novi = token(napravi(null));
		String zaSto7 = token(napravi(sto7));

		mockMvc.perform(get("/api/screens/{token}", stari))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Ekran ne postoji"));
		mockMvc.perform(get("/api/screens/{token}", novi)).andExpect(jsonPath("$.resources", hasSize(2)));
		mockMvc.perform(get("/api/screens/{token}", zaSto7))
				.andExpect(jsonPath("$.resources[*].name", contains("Sto 7, uz prozor")));
		mockMvc.perform(get("/api/organizations/{id}/screens", citaonica).header("Authorization", "Bearer " + admin))
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].resourceName").value("Sto 7, uz prozor"))
				.andExpect(jsonPath("$[1].resourceId").isEmpty());
		assertThat(jdbc.queryForObject("select count(*) from audit_log where organization_id = ? and action = "
				+ "'DOOR_SCREEN_REVOKED'", Long.class, citaonica)).isEqualTo(1);
	}

	@Test
	void ugasenEkranJe404IZatvoriOtvorenuVezu() throws Exception {
		String odgovor = napravi(null).andReturn().getResponse().getContentAsString();
		UUID ekran = UUID.fromString(JsonPath.read(odgovor, "$.id"));
		String token = token(odgovor);
		otvori(token);
		assertThat(screenStream.brojVeza(ekran)).isEqualTo(1);

		mockMvc.perform(delete("/api/organizations/{id}/screens/{s}", citaonica, ekran)
				.header("Authorization", "Bearer " + admin)).andExpect(status().isNoContent());

		assertThat(screenStream.brojVeza(ekran)).isZero();
		mockMvc.perform(get("/api/screens/{token}", token)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/screens/{token}/stream", token))
				.andExpect(status().isNotFound())
				.andExpect(request().asyncNotStarted());
		mockMvc.perform(get("/api/organizations/{id}/screens", citaonica).header("Authorization", "Bearer " + admin))
				.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void rezervacijaJaviEkranuKojiPratiTajResursANeDrugom() throws Exception {
		MockHttpServletResponse sviStolovi = otvori(token(napravi(null)));
		MockHttpServletResponse samoSto7 = otvori(token(napravi(sto7)));

		Instant sutra = LocalDate.now(ZONA).plusDays(1).atTime(10, 0).atZone(ZONA).toInstant();
		bookingService.create(sto4, lejla, sutra, null);

		assertThat(sviStolovi.getContentAsString()).contains("event:promjena\ndata:" + sto4);
		assertThat(samoSto7.getContentAsString()).doesNotContain("promjena");
	}

	@Test
	void izmisljenTokenJe404() throws Exception {
		mockMvc.perform(get("/api/screens/{token}", "nije-pravi-token"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value(
						"Ovaj ekran je ugašen ili link nije tačan. Admin organizacije može napraviti novi u postavkama."));
	}

	private ResultActions napravi(UUID resurs) throws Exception {
		return mockMvc.perform(post("/api/organizations/{id}/screens", citaonica)
				.header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
				.content(resurs == null ? "{}" : "{\"resourceId\": \"" + resurs + "\"}"));
	}

	private static String token(ResultActions odgovor) throws Exception {
		return token(odgovor.andReturn().getResponse().getContentAsString());
	}

	private static String token(String odgovor) {
		String url = JsonPath.read(odgovor, "$.url");
		return url.substring(url.lastIndexOf('/') + 1);
	}

	private MockHttpServletResponse otvori(String token) throws Exception {
		return mockMvc.perform(get("/api/screens/{token}/stream", token))
				.andExpect(status().isOk())
				.andExpect(request().asyncStarted())
				.andReturn().getResponse();
	}

	private UUID sto(String naziv) {
		UUID sto = resourceService.create(citaonica, amila, naziv, null, 60, true, 0, 1, 0).id();
		availabilityService.replace(sto, Arrays.stream(DayOfWeek.values())
				.map(d -> new AvailabilityInterval(d, LocalTime.of(8, 0), LocalTime.of(16, 0))).toList());
		return sto;
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
		return ime + ".ekran." + sufiks + "@example.com";
	}

}
