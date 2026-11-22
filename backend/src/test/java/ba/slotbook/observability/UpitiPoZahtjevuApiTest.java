package ba.slotbook.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.availability.AvailabilityInterval;
import ba.slotbook.availability.AvailabilityService;
import ba.slotbook.booking.BookingService;
import ba.slotbook.booking.CalendarFeedService;
import ba.slotbook.booking.WaitlistService;
import ba.slotbook.organization.OrganizationService;
import ba.slotbook.resource.ResourceService;
import ba.slotbook.user.UserService;

// N+1 se vidi tek kad broj upita raste s brojem redova. Zato se svaka lista mjeri dvaput, s jednim
// i sa šest redova, i broj SQL upita mora ostati isti.
@Import({ TestcontainersConfiguration.class, UpitiPoZahtjevuApiTest.Brojanje.class })
@SpringBootTest
@AutoConfigureMockMvc
class UpitiPoZahtjevuApiTest {

	private static final String LOZINKA = "vrelo-bosne-ilidza";
	private static final String ZONA = "Europe/Sarajevo";
	private static final String PONEDJELJAK = "2035-11-19";
	private static final String UTORAK = "2035-11-20";
	private static final String SUFIKS = UUID.randomUUID().toString().substring(0, 6);

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
	WaitlistService waitlistService;

	@Autowired
	CalendarFeedService calendarFeedService;

	private UUID mirza;
	private UUID lejla;
	private UUID teretana;
	private UUID salaJedan;

	@Test
	void brojUpitaNaListamaNeRasteSBrojemRedova() throws Exception {
		mirza = korisnik("mirza", "Mirza Hadžić");
		lejla = korisnik("lejla", "Lejla Čaušević");
		teretana = organizationService.create(mirza, "Teretana Bjelave", "teretana-bjelave-" + SUFIKS, ZONA).id();
		clan(teretana, lejla);
		dodaj(1);
		String mirzinToken = prijava("mirza");
		String lejlinToken = prijava("lejla");
		String lejlinLink = calendarFeedService.replace(lejla).token();

		Map<String, Integer> jedan = izmjeri(mirzinToken, lejlinToken, lejlinLink);
		for (int i = 2; i <= 6; i++) {
			dodaj(i);
		}
		Map<String, Integer> sest = izmjeri(mirzinToken, lejlinToken, lejlinLink);

		System.out.println("Upiti po zahtjevu, 1 red: " + jedan + ", 6 redova: " + sest);
		assertThat(sest).isEqualTo(jedan);
	}

	// Svaki krug doda po jedan red u svaku listu: člana, salu, Lejlinu rezervaciju, rezervaciju u
	// rasporedu prve sale (drugi klijent, pa drugo ime), Lejlin upis na listu čekanja za taj termin
	// i organizaciju u kojoj je Lejla članica
	private void dodaj(int i) {
		UUID klijent = korisnik("klijent" + i, "Klijent " + i);
		clan(teretana, klijent);
		UUID sala = resourceService.create(teretana, mirza, "Sala " + i, null, 60, true, 0).id();
		availabilityService.replace(sala,
				List.of(new AvailabilityInterval(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(20, 0))));
		if (i == 1) {
			salaJedan = sala;
		}
		bookingService.create(sala, lejla, pocetak(8), null);
		bookingService.create(salaJedan, klijent, pocetak(9 + i), null);
		waitlistService.join(salaJedan, lejla, pocetak(9 + i));
		UUID druga = organizationService.create(mirza, "Teretana " + i, "teretana-" + i + "-" + SUFIKS, ZONA).id();
		clan(druga, lejla);
	}

	private Map<String, Integer> izmjeri(String mirzinToken, String lejlinToken, String lejlinLink) throws Exception {
		Map<String, Integer> broj = new LinkedHashMap<>();
		broj.put("resursi", upiti(get("/api/organizations/{id}/resources", teretana), mirzinToken));
		broj.put("clanovi", upiti(get("/api/organizations/{id}/members", teretana), mirzinToken));
		broj.put("mojeRezervacije", upiti(get("/api/bookings/mine"), lejlinToken));
		broj.put("raspored", upiti(get("/api/resources/{id}/bookings", salaJedan)
				.param("from", PONEDJELJAK).param("to", UTORAK), mirzinToken));
		broj.put("slobodniTermini", upiti(get("/api/resources/{id}/slots", salaJedan)
				.param("from", PONEDJELJAK).param("to", UTORAK), lejlinToken));
		broj.put("mojaListaCekanja", upiti(get("/api/waitlist/mine"), lejlinToken));
		// Javna putanja, JWT joj ne smeta, a pravo daje token iz linka
		broj.put("kalendarIcs", upiti(get("/api/me/calendar.ics").param("token", lejlinLink), lejlinToken));
		broj.put("mojeOrganizacije", upiti(get("/api/me/organizations"), lejlinToken));
		broj.put("dnevnik", upiti(get("/api/organizations/{id}/audit-log", teretana), mirzinToken));
		return broj;
	}

	private int upiti(MockHttpServletRequestBuilder zahtjev, String token) throws Exception {
		BrojacUpita.pocni();
		try {
			mockMvc.perform(zahtjev.header("Authorization", "Bearer " + token)).andExpect(status().isOk());
			return BrojacUpita.upiti().size();
		} finally {
			BrojacUpita.zavrsi();
		}
	}

	private Instant pocetak(int sat) {
		return Instant.parse(PONEDJELJAK + "T%02d:00:00+01:00".formatted(sat));
	}

	private UUID korisnik(String ime, String prikaz) {
		return userService.register(email(ime), LOZINKA, prikaz).id();
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

	private static String email(String ime) {
		return ime + "." + SUFIKS + "@example.com";
	}

	// Broji samo upite iz niti testa. Hibernate statistika je zajednička za cijelu aplikaciju, pa bi
	// u nju upao i BookingJob koji radi svake minute.
	static class BrojacUpita implements StatementInspector {

		private static final ThreadLocal<List<String>> UPITI = new ThreadLocal<>();

		static void pocni() {
			UPITI.set(new ArrayList<>());
		}

		static List<String> upiti() {
			return UPITI.get();
		}

		static void zavrsi() {
			UPITI.remove();
		}

		@Override
		public String inspect(String sql) {
			List<String> upiti = UPITI.get();
			if (upiti != null) {
				upiti.add(sql);
			}
			return sql;
		}

	}

	@TestConfiguration
	static class Brojanje {

		@Bean
		HibernatePropertiesCustomizer brojacUpita() {
			return svojstva -> svojstva.put(AvailableSettings.STATEMENT_INSPECTOR, new BrojacUpita());
		}

	}

}
