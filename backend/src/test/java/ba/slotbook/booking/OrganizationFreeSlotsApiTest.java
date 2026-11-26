package ba.slotbook.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
import jakarta.persistence.EntityManagerFactory;

// Čitaonica s dva stola koji rade svaki dan od 08 do 16. Statistika Hibernatea je uključena samo
// ovdje, da se izbroje SQL upiti jednog zahtjeva.
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
class OrganizationFreeSlotsApiTest {

	private static final String LOZINKA = "nadji-slobodno-sto";
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
	EntityManagerFactory emf;

	private final String sufiks = UUID.randomUUID().toString().substring(0, 6);
	private UUID amila;
	private UUID lejla;
	private UUID citaonica;
	private UUID sto4;
	private UUID sto7;
	private String lejlin;
	private String amilin;
	private final LocalDate sutra = LocalDate.now(ZONA).plusDays(1);

	@BeforeEach
	void citaonica() throws Exception {
		amila = korisnik("amila");
		lejla = korisnik("lejla");
		citaonica = organizationService.create(amila, "Čitaonica slobodno", "citaonica-slobodno-" + sufiks,
				ZONA.getId()).id();
		jdbc.update("insert into organization_members values (?, ?, 'CLIENT', now())", citaonica, lejla);
		sto4 = sto("Sto 4, tihi dio");
		sto7 = sto("Sto 7, uz prozor");
		lejlin = prijava("lejla");
		amilin = prijava("amila");
	}

	@Test
	void sutraPoslije14PokazujeSlobodneTermineObaStolaPoVremenuBezZauzetog() throws Exception {
		bookingService.create(sto4, amila, sutra.atTime(14, 0).atZone(ZONA).toInstant(), null);

		trazi(lejlin, sutra, 1, 14, 16)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.timezone").value("Europe/Sarajevo"))
				.andExpect(jsonPath("$.slots[*].resourceName",
						contains("Sto 7, uz prozor", "Sto 4, tihi dio", "Sto 7, uz prozor")))
				.andExpect(jsonPath("$.slots[*].start", contains(u(14), u(15), u(15))))
				.andExpect(jsonPath("$.slots[0].seatsLeft").value(1));
	}

	// Sto 7 se rezerviše samo dan unaprijed: klijent za prekosutra vidi samo sto 4, a admin oba
	@Test
	void klijentuSeNeNudiTerminVanPravilaResursaAAdminuSeNudi() throws Exception {
		jdbc.update("update resources set max_days_ahead = 1 where id = ?", sto7);
		LocalDate prekosutra = sutra.plusDays(1);

		String klijent = trazi(lejlin, prekosutra, 1, 8, 9).andReturn().getResponse().getContentAsString();
		String admin = trazi(amilin, prekosutra, 1, 8, 9).andReturn().getResponse().getContentAsString();

		assertThat(JsonPath.<java.util.List<String>>read(klijent, "$.slots[*].resourceName"))
				.containsExactly("Sto 4, tihi dio");
		assertThat(JsonPath.<java.util.List<String>>read(admin, "$.slots[*].resourceName"))
				.containsExactly("Sto 4, tihi dio", "Sto 7, uz prozor");
	}

	@Test
	void triDanaIIzvrnutiSatiSu400SPorukomZaPolje() throws Exception {
		trazi(lejlin, sutra, 3, 0, 24)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.greske[0].polje").value("days"));
		trazi(lejlin, sutra, 1, 16, 14)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.greske[0].polje").value("fromHour"));
	}

	// Broj upita raste s brojem resursa, jer se svaki resurs računa kao u kalendaru. Mjeri se da raste
	// linearno i za koliko, a brojke su u docs/ODLUKE.md.
	@Test
	void brojUpitaRasteLinearnoSBrojemResursa() throws Exception {
		jdbc.update("update resources set active = false where id = ?", sto7);
		long jedan = upitaZa2Dana();
		for (int i = 2; i <= 6; i++) {
			sto("Sto " + (10 + i));
		}
		long sest = upitaZa2Dana();

		long poResursu = (sest - jedan) / 5;
		assertThat(sest - jedan).isEqualTo(5 * poResursu);
		assertThat(poResursu).isLessThanOrEqualTo(7);
		System.out.printf("free-slots, 2 dana: 1 resurs %d upita, 6 resursa %d upita, %d po resursu%n", jedan, sest,
				poResursu);
	}

	private long upitaZa2Dana() throws Exception {
		Statistics statistika = emf.unwrap(SessionFactory.class).getStatistics();
		statistika.clear();
		trazi(lejlin, sutra, 2, 0, 24).andExpect(status().isOk());
		return statistika.getPrepareStatementCount();
	}

	private ResultActions trazi(String token, LocalDate dan, int dana, int od, int doSata) throws Exception {
		return mockMvc.perform(get("/api/organizations/{id}/free-slots", citaonica)
				.param("date", dan.toString()).param("days", String.valueOf(dana))
				.param("fromHour", String.valueOf(od)).param("toHour", String.valueOf(doSata))
				.header("Authorization", "Bearer " + token));
	}

	private String u(int sat) {
		return sutra.atTime(sat, 0).atZone(ZONA).toInstant().toString();
	}

	private UUID sto(String naziv) {
		UUID sto = resourceService.create(citaonica, amila, naziv, null, 60, true, 0, 1, 0).id();
		availabilityService.replace(sto, Arrays.stream(DayOfWeek.values())
				.map(d -> new AvailabilityInterval(d, LocalTime.of(8, 0), LocalTime.of(16, 0))).toList());
		return sto;
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
		return ime + ".slobodno." + sufiks + "@example.com";
	}

}
