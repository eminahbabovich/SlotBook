package ba.slotbook.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Duration;
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

// BookingJob radi i u testu, svake minute. Zato testovi gledaju stanje u bazi poslije offerFreed, a ne
// koliko je ponuda baš ovaj poziv napravio: posao je mogao ponuditi termin malo prije.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class WaitlistApiTest {

	private static final String LOZINKA = "trebevic-zicara-1959";
	// Ponedjeljak, 19.11.2035. u 09:00 po sarajevskom vremenu
	private static final String TERMIN = "2035-11-19T09:00:00+01:00";

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

	private String oznaka;
	private UUID sala;
	private UUID hana;
	private String hanin;
	private String lejlin;
	private String selmin;

	// Hana drži termin u 09:00, a Lejla i Selma su članice koje bi ga htjele
	@BeforeEach
	void hanaDrziTerminUSali() throws Exception {
		oznaka = UUID.randomUUID().toString().substring(0, 8);
		UUID mirza = korisnik("mirza", "Mirza Hadžić");
		hana = korisnik("hana", "Hana Kovačević");
		UUID teretana = organizationService.create(mirza, "Teretana Koševo", "teretana-kosevo-" + oznaka,
				"Europe/Sarajevo").id();
		sala = resourceService.create(teretana, mirza, "Sala za grupni trening", null, 60, true, 0).id();
		availabilityService.replace(sala,
				List.of(new AvailabilityInterval(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(20, 0))));
		for (String ime : List.of("hana", "lejla", "selma")) {
			UUID id = ime.equals("hana") ? hana : korisnik(ime, ime.equals("lejla") ? "Lejla Đozić" : "Selma Šabić");
			jdbc.update("insert into organization_members values (?, ?, 'CLIENT', now())", teretana, id);
		}
		hanin = prijava("hana");
		lejlin = prijava("lejla");
		selmin = prijava("selma");
		rezervisi(hanin).andExpect(status().isCreated());
	}

	@Test
	void prviNaListiDobijaPonuduIDrugiNeMozeUzetiTerminDokPonudaTraje() throws Exception {
		String lejlinUpis = upisi(lejlin);
		String selminUpis = upisi(selmin);

		otkaziHaninu();
		waitlistService.offerFreed();

		assertThat(stanje(lejlinUpis)).isEqualTo("OFFERED");
		assertThat(stanje(selminUpis)).isEqualTo("WAITING");
		assertThat(jdbc.queryForObject("select offered_until from waitlist_entries where id = ?::uuid",
				Instant.class, lejlinUpis)).isBetween(Instant.now().plus(Duration.ofMinutes(29)),
						Instant.now().plus(Duration.ofMinutes(31)));
		assertThat(jdbc.queryForObject("select body from notifications n join users u on u.id = n.user_id "
				+ "where n.type = 'WAITLIST_OFFER' and u.email = ?", String.class, email("lejla")))
				.contains("ponedjeljak, 19. novembar 2035. u 09:00").contains("/r/" + sala);

		rezervisi(selmin).andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Termin je ponuđen drugome"));
		rezervisi(lejlin).andExpect(status().isCreated());
		assertThat(stanje(lejlinUpis)).isEqualTo("BOOKED");
	}

	@Test
	void ponudaKojojProdeRokIdeSljedecemNaListi() throws Exception {
		String lejlinUpis = upisi(lejlin);
		String selminUpis = upisi(selmin);
		otkaziHaninu();
		waitlistService.offerFreed();

		jdbc.update("update waitlist_entries set offered_until = now() - interval '1 minute' where id = ?::uuid",
				lejlinUpis);
		waitlistService.offerFreed();

		assertThat(stanje(lejlinUpis)).isEqualTo("EXPIRED");
		assertThat(stanje(selminUpis)).isEqualTo("OFFERED");
	}

	@Test
	void naSlobodanIliSvojTerminSeNeUpisujeNaListu() throws Exception {
		upis(lejlin, "2035-11-19T10:00:00+01:00").andExpect(status().isConflict())
				.andExpect(jsonPath("$.razlog").value("SLOBODAN"));
		upis(hanin, TERMIN).andExpect(status().isConflict())
				.andExpect(jsonPath("$.razlog").value("TVOJ"));
		assertThat(brojUpisa()).isZero();
	}

	@Test
	void ponovljenUpisVracaIstiUpis() throws Exception {
		assertThat(upisi(lejlin)).isEqualTo(upisi(lejlin));
		assertThat(brojUpisa()).isEqualTo(1);
	}

	@Test
	void ispisanaKlijentkinjaNeDobijaPonudu() throws Exception {
		String lejlinUpis = upisi(lejlin);
		mockMvc.perform(delete("/api/waitlist/{id}", lejlinUpis).header("Authorization", "Bearer " + selmin))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/waitlist/{id}", lejlinUpis).header("Authorization", "Bearer " + lejlin))
				.andExpect(status().isNoContent());

		otkaziHaninu();
		waitlistService.offerFreed();

		assertThat(stanje(lejlinUpis)).isEqualTo("CANCELLED");
		assertThat(jdbc.queryForObject("select count(*) from notifications n join users u on u.id = n.user_id "
				+ "where n.type = 'WAITLIST_OFFER' and u.email = ?", Long.class, email("lejla"))).isZero();
	}

	private void otkaziHaninu() {
		UUID id = jdbc.queryForObject("select id from bookings where resource_id = ? and client_id = ?", UUID.class,
				sala, hana);
		bookingService.cancel(id, hana, false);
	}

	private ResultActions rezervisi(String token) throws Exception {
		return mockMvc.perform(post("/api/resources/{id}/bookings", sala).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"startTime": "%s"}
						""".formatted(TERMIN)));
	}

	private ResultActions upis(String token, String pocetak) throws Exception {
		return mockMvc.perform(post("/api/resources/{id}/waitlist", sala).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"startTime": "%s"}
						""".formatted(pocetak)));
	}

	private String upisi(String token) throws Exception {
		String tijelo = upis(token, TERMIN).andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("WAITING"))
				.andExpect(jsonPath("$.endTime").value("2035-11-19T09:00:00Z"))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(tijelo, "$.id");
	}

	private String stanje(String upis) {
		return jdbc.queryForObject("select status from waitlist_entries where id = ?::uuid", String.class, upis);
	}

	private long brojUpisa() {
		return jdbc.queryForObject("select count(*) from waitlist_entries where resource_id = ?", Long.class, sala);
	}

	private UUID korisnik(String ime, String prikaz) {
		return userService.register(email(ime), LOZINKA, prikaz).id();
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
		return ime + "." + oznaka + "@example.com";
	}

}
