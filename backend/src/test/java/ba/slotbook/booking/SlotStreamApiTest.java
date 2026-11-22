package ba.slotbook.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.availability.AvailabilityInterval;
import ba.slotbook.availability.AvailabilityService;
import ba.slotbook.organization.OrganizationService;
import ba.slotbook.resource.ResourceService;
import ba.slotbook.user.UserService;

// MockMvc ne zatvara SSE vezu, nego drži odgovor otvoren, a sve što emitter pošalje odmah je u
// tijelu odgovora. Tako se vidi šta je stiglo do kalendara, bez pravog browsera.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SlotStreamApiTest {

	private static final String LOZINKA = "bascarsija-u-podne";
	private static final String ZONA = "Europe/Sarajevo";

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
	SlotStream slotStream;

	@Autowired
	WaitlistService waitlistService;

	@Autowired
	PlatformTransactionManager transakcije;

	private final String sufiks = UUID.randomUUID().toString().substring(0, 6);
	private UUID lejla;
	private UUID amar;
	private UUID salaZaJogu;
	private UUID salaZaPilates;

	@BeforeEach
	void teretana() {
		UUID mirza = korisnik("mirza");
		lejla = korisnik("lejla");
		amar = korisnik("amar");
		UUID teretana = organizationService.create(mirza, "Teretana Bjelave", "bjelave-sse-" + sufiks, ZONA).id();
		clan(teretana, lejla);
		clan(teretana, amar);
		salaZaJogu = sala(teretana, mirza, "Sala za jogu");
		salaZaPilates = sala(teretana, mirza, "Sala za pilates");
	}

	@Test
	void rezervacijaJaviOtvorenomKalendaruIstogResursaANeDrugog() throws Exception {
		MockHttpServletResponse joga = otvori(salaZaJogu, prijava("lejla"));
		MockHttpServletResponse pilates = otvori(salaZaPilates, prijava("lejla"));
		assertThat(joga.getContentAsString()).startsWith(":spojeno\n\n");

		bookingService.create(salaZaJogu, amar, pocetak(9), null);

		assertThat(joga.getContentAsString(StandardCharsets.UTF_8))
				.contains("event:promjena\ndata:" + salaZaJogu + "\n\n");
		assertThat(pilates.getContentAsString()).doesNotContain("promjena");
	}

	// Signal ide tek poslije commita. Rezervacija koju je odbio EXCLUDE nije ništa promijenila.
	@Test
	void zauzetTerminNeJavljaNistaJerSeTransakcijaVratila() throws Exception {
		bookingService.create(salaZaJogu, amar, pocetak(10), null);
		MockHttpServletResponse joga = otvori(salaZaJogu, prijava("lejla"));

		assertThatThrownBy(() -> bookingService.create(salaZaJogu, lejla, pocetak(10), null))
				.isInstanceOf(SlotTakenException.class);

		assertThat(joga.getContentAsString()).isEqualTo(":spojeno\n\n");
	}

	// Kalendar na signal odmah pita /slots. Da signal ide prije commita, dobio bi staro stanje.
	@Test
	void signalStizeTekKadSeTransakcijaCommita() throws Exception {
		MockHttpServletResponse joga = otvori(salaZaJogu, prijava("lejla"));

		new TransactionTemplate(transakcije).executeWithoutResult(transakcija -> {
			bookingService.create(salaZaJogu, amar, pocetak(12), null);
			assertThat(sadrzaj(joga)).doesNotContain("promjena");
		});

		assertThat(sadrzaj(joga)).contains("event:promjena");
	}

	@Test
	void ponudaSListeCekanjaJaviKalendaru() throws Exception {
		UUID rezervacija = bookingService.create(salaZaJogu, amar, pocetak(13), null).id();
		waitlistService.join(salaZaJogu, lejla, pocetak(13));
		bookingService.cancel(rezervacija, amar, false);
		MockHttpServletResponse joga = otvori(salaZaJogu, prijava("lejla"));

		assertThat(waitlistService.offerFreed()).isPositive();

		assertThat(sadrzaj(joga)).contains("event:promjena");
	}

	@Test
	void otkazivanjeJaviKalendaru() throws Exception {
		UUID rezervacija = bookingService.create(salaZaJogu, amar, pocetak(11), null).id();
		MockHttpServletResponse joga = otvori(salaZaJogu, prijava("lejla"));

		bookingService.cancel(rezervacija, amar, false);

		assertThat(joga.getContentAsString()).contains("event:promjena");
	}

	@Test
	void korisnikIzDrugeOrganizacijeNeOtvaraTok() throws Exception {
		UUID tarik = korisnik("tarik");
		organizationService.create(tarik, "Čitaonica PMF", "pmf-sse-" + sufiks, ZONA);
		int prije = slotStream.brojVeza(salaZaJogu);

		mockMvc.perform(get("/api/resources/{id}/slots/stream", salaZaJogu)
				.header("Authorization", "Bearer " + prijava("tarik")))
				.andExpect(status().isForbidden())
				.andExpect(request().asyncNotStarted());

		assertThat(slotStream.brojVeza(salaZaJogu)).isEqualTo(prije);
	}

	private MockHttpServletResponse otvori(UUID resurs, String token) throws Exception {
		return mockMvc.perform(get("/api/resources/{id}/slots/stream", resurs).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(request().asyncStarted())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
				.andReturn().getResponse();
	}

	private static String sadrzaj(MockHttpServletResponse odgovor) {
		try {
			return odgovor.getContentAsString(StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private UUID sala(UUID teretana, UUID mirza, String naziv) {
		UUID sala = resourceService.create(teretana, mirza, naziv, null, 60, true, 0, 1).id();
		availabilityService.replace(sala,
				List.of(new AvailabilityInterval(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(20, 0))));
		return sala;
	}

	// 19.11.2035. je ponedjeljak
	private static Instant pocetak(int sat) {
		return Instant.parse("2035-11-19T%02d:00:00+01:00".formatted(sat));
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
		return ime + ".sse." + sufiks + "@example.com";
	}

}
