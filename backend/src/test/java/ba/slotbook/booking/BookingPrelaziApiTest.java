package ba.slotbook.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import ba.slotbook.user.UserRepository;

// Ko smije koji prelaz, i da nedozvoljen prelaz vraća 409
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class BookingPrelaziApiTest {

	private static final String LOZINKA = "visegrad-cuprija-1577";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	BookingService bookingService;

	private String selma;
	private String hana;
	private String lamija;
	private UUID katedra;

	// Selma je profesorica i admin katedre, Hana i Lamija studentice (CLIENT)
	@BeforeEach
	void katedraSaDvijeStudentice() throws Exception {
		selma = token("selma.prelazi@example.com", "Selma");
		hana = token("hana.prelazi@example.com", "Hana");
		lamija = token("lamija.prelazi@example.com", "Lamija");
		var postojeca = jdbc.queryForList("select id from organizations where slug = 'katedra-prelazi'", UUID.class);
		katedra = postojeca.isEmpty() ? novaOrganizacija() : postojeca.getFirst();
		for (String email : new String[] { "hana.prelazi@example.com", "lamija.prelazi@example.com" }) {
			jdbc.update("insert into organization_members values (?, ?, 'CLIENT', now()) on conflict do nothing",
					katedra, id(email));
		}
	}

	private UUID id(String email) {
		return users.findByEmail(email).orElseThrow().getId();
	}

	private String token(String email, String ime) throws Exception {
		if (users.findByEmail(email).isEmpty()) {
			mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
					{"email": "%s", "password": "%s", "displayName": "%s"}
					""".formatted(email, LOZINKA, ime))).andExpect(status().isCreated());
			jdbc.update("update users set email_verified_at = now() where email = lower(?)", email);
		}
		String tijelo = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "%s"}
				""".formatted(email, LOZINKA))).andReturn().getResponse().getContentAsString();
		return JsonPath.read(tijelo, "$.accessToken");
	}

	private UUID novaOrganizacija() throws Exception {
		return idIz(mockMvc.perform(post("/api/organizations")
				.header("Authorization", "Bearer " + selma)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Katedra za informatiku", "slug": "katedra-prelazi", "timezone": "Europe/Sarajevo"}
						""")));
	}

	private UUID idIz(ResultActions odgovor) throws Exception {
		return UUID.fromString(JsonPath.read(odgovor.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), "$.id"));
	}

	// Satni termini ponedjeljkom od 08 do 12. Konsultacije čekaju potvrdu, čitaonica potvrđuje sama.
	private UUID resurs(boolean autoConfirm) throws Exception {
		UUID resurs = idIz(mockMvc.perform(post("/api/organizations/{id}/resources", katedra)
				.header("Authorization", "Bearer " + selma)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Konsultacije %s", "slotMinutes": 60, "autoConfirm": %s}
						""".formatted(UUID.randomUUID().toString().substring(0, 8), autoConfirm))));
		mockMvc.perform(put("/api/resources/{id}/availability", resurs)
				.header("Authorization", "Bearer " + selma)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"rules": [{"dayOfWeek": "MONDAY", "start": "08:00", "end": "12:00"}]}
						"""))
				.andExpect(status().isOk());
		return resurs;
	}

	private ResultActions rezervisi(String token, UUID resurs) throws Exception {
		return mockMvc.perform(post("/api/resources/{id}/bookings", resurs)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"startTime": "2035-11-19T09:00:00+01:00"}
						"""));
	}

	private UUID rezervacija(String token, UUID resurs) throws Exception {
		return idIz(rezervisi(token, resurs));
	}

	private ResultActions prelaz(String token, UUID rezervacija, String akcija) throws Exception {
		return mockMvc.perform(post("/api/bookings/{id}/" + akcija, rezervacija)
				.header("Authorization", "Bearer " + token));
	}

	private String stanje(UUID rezervacija) {
		return jdbc.queryForObject("select status from bookings where id = ?", String.class, rezervacija);
	}

	@Test
	void profesoricaPotvrdjujeRezervacijuNaCekanjuIToIdeUHistoriju() throws Exception {
		UUID rezervacija = rezervacija(hana, resurs(false));

		prelaz(selma, rezervacija, "confirm")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.expiresAt").value(nullValue()));

		var zadnji = jdbc.queryForMap("select from_status, to_status, actor_id from booking_events "
				+ "where booking_id = ? and from_status is not null", rezervacija);
		assertThat(zadnji).containsEntry("from_status", "PENDING").containsEntry("to_status", "CONFIRMED")
				.containsEntry("actor_id", id("selma.prelazi@example.com"));
	}

	@Test
	void profesoricaOdbijaRezervacijuNaCekanju() throws Exception {
		UUID rezervacija = rezervacija(hana, resurs(false));

		prelaz(selma, rezervacija, "reject").andExpect(jsonPath("$.status").value("REJECTED"));
	}

	@Test
	void studenticaNeMozeSamaPotvrditiSvojuRezervaciju() throws Exception {
		UUID rezervacija = rezervacija(hana, resurs(false));

		prelaz(hana, rezervacija, "confirm").andExpect(status().isForbidden());
		assertThat(stanje(rezervacija)).isEqualTo("PENDING");
	}

	// Poslije otkazivanja termin se opet nudi, i Lamija ga može uzeti
	@Test
	void studenticaOtkazujeSvojuRezervacijuITerminSeOslobadja() throws Exception {
		UUID citaonica = resurs(true);
		UUID rezervacija = rezervacija(hana, citaonica);

		prelaz(hana, rezervacija, "cancel")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));

		mockMvc.perform(get("/api/resources/{id}/slots", citaonica)
				.param("from", "2035-11-19").param("to", "2035-11-20")
				.header("Authorization", "Bearer " + lamija))
				.andExpect(jsonPath("$.slots[*].start", hasItem("2035-11-19T08:00:00Z")));
		rezervisi(lamija, citaonica).andExpect(status().isCreated());
	}

	@Test
	void studenticaNeMozeOtkazatiTudjuRezervaciju() throws Exception {
		UUID rezervacija = rezervacija(hana, resurs(true));

		prelaz(lamija, rezervacija, "cancel").andExpect(status().isForbidden());
		assertThat(stanje(rezervacija)).isEqualTo("CONFIRMED");
	}

	@Test
	void otkazanaRezervacijaSeNeMozePotvrditi() throws Exception {
		UUID rezervacija = rezervacija(hana, resurs(false));
		prelaz(hana, rezervacija, "cancel").andExpect(status().isOk());

		prelaz(selma, rezervacija, "confirm")
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.trenutniStatus").value("CANCELLED"))
				.andExpect(jsonPath("$.trazeniStatus").value("CONFIRMED"));
		assertThat(stanje(rezervacija)).isEqualTo("CANCELLED");
	}

	// Termin je 2035. godine, pa nedolazak još ne može, a dolazak može
	@Test
	void nedolazakPrijePocetkaJe409ADolazakZavrsavaRezervaciju() throws Exception {
		UUID rezervacija = rezervacija(hana, resurs(true));

		prelaz(selma, rezervacija, "no-show")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Termin još nije počeo"));
		prelaz(selma, rezervacija, "check-in").andExpect(jsonPath("$.status").value("COMPLETED"));
	}

	// Termin za sat vremena na resursu s rokom od 2 sata. Red se upisuje direktno, jer API ne prima
	// termine iz prošlosti ni van radnog vremena, a ovdje treba termin blizu sadašnjeg trenutka.
	private UUID uskoroNaResursuSRokom() throws Exception {
		UUID resurs = idIz(mockMvc.perform(post("/api/organizations/{id}/resources", katedra)
				.header("Authorization", "Bearer " + selma)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Konsultacije %s", "slotMinutes": 60, "cancelNoticeHours": 2}
						""".formatted(UUID.randomUUID().toString().substring(0, 8)))));
		UUID rezervacija = UUID.randomUUID();
		jdbc.update("insert into bookings (id, resource_id, client_id, start_time, end_time, status, created_at) "
				+ "values (?, ?, ?, now() + interval '1 hour', now() + interval '2 hours', 'CONFIRMED', now())",
				rezervacija, resurs, id("hana.prelazi@example.com"));
		return rezervacija;
	}

	@Test
	void studenticaNeMozeOtkazatiPoslijeRoka() throws Exception {
		UUID rezervacija = uskoroNaResursuSRokom();

		prelaz(hana, rezervacija, "cancel")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Rok za otkazivanje je prošao"))
				.andExpect(jsonPath("$.rokZaOtkazivanje").exists());
		assertThat(stanje(rezervacija)).isEqualTo("CONFIRMED");
	}

	@Test
	void profesoricaOtkazujeIPoslijeRoka() throws Exception {
		UUID rezervacija = uskoroNaResursuSRokom();

		prelaz(selma, rezervacija, "cancel").andExpect(jsonPath("$.status").value("CANCELLED"));
	}

	@Test
	void historijaPokazujeKoJeStaPromijenioRedom() throws Exception {
		UUID rezervacija = rezervacija(hana, resurs(false));
		prelaz(selma, rezervacija, "confirm").andExpect(status().isOk());

		mockMvc.perform(get("/api/bookings/{id}/events", rezervacija).header("Authorization", "Bearer " + hana))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].toStatus", contains("PENDING", "CONFIRMED")))
				.andExpect(jsonPath("$[*].actorName", contains("Hana", "Selma")))
				.andExpect(jsonPath("$[0].fromStatus").doesNotExist());
	}

	@Test
	void historijuTudjeRezervacijeStudenticaNeVidi() throws Exception {
		UUID rezervacija = rezervacija(hana, resurs(true));

		mockMvc.perform(get("/api/bookings/{id}/events", rezervacija).header("Authorization", "Bearer " + lamija))
				.andExpect(status().isForbidden());
	}

	// Istek nema aktera, a historija ga i dalje prikazuje
	@Test
	void historijaPokazujeIIstekBezImena() throws Exception {
		UUID rezervacija = rezervacija(hana, resurs(false));
		jdbc.update("update bookings set expires_at = now() - interval '1 minute' where id = ?", rezervacija);
		bookingService.expireOverdue();

		mockMvc.perform(get("/api/bookings/{id}/events", rezervacija).header("Authorization", "Bearer " + hana))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].toStatus", contains("PENDING", "EXPIRED")))
				.andExpect(jsonPath("$[1].actorName").value(nullValue()));
	}

	// Raspored za providera: ko je rezervisao i u kom je stanju. Klijent ga ne vidi, jer bi vidio
	// imena drugih studentica.
	@Test
	void rasporedResursaImaImeKlijentaIStatusAKlijentGaNeVidi() throws Exception {
		UUID resurs = resurs(false);
		rezervisi(hana, resurs).andExpect(status().isCreated());

		mockMvc.perform(get("/api/resources/{id}/bookings", resurs).param("from", "2035-11-19").param("to", "2035-11-20")
				.header("Authorization", "Bearer " + selma))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].clientName", contains("Hana")))
				.andExpect(jsonPath("$[0].startTime").value("2035-11-19T08:00:00Z"))
				.andExpect(jsonPath("$[0].status").value("PENDING"));
		mockMvc.perform(get("/api/resources/{id}/bookings", resurs).param("from", "2035-11-20").param("to", "2035-11-21")
				.header("Authorization", "Bearer " + selma))
				.andExpect(jsonPath("$").isEmpty());
		mockMvc.perform(get("/api/resources/{id}/bookings", resurs).param("from", "2035-11-19").param("to", "2035-11-20")
				.header("Authorization", "Bearer " + lamija))
				.andExpect(status().isForbidden());
	}
}
