package ba.slotbook.booking;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.user.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class BookingApiTest {

	private static final String LOZINKA = "stari-most-1566";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	private String amila;
	private String hana;
	private String lamija;
	private UUID citaonica;

	// Amila je admin čitaonice, Hana i Lamija studentice (CLIENT). Baza se dijeli između testova,
	// pa svaki test pravi svoj sto, a korisnici i organizacija se prave samo jednom.
	@BeforeEach
	void citaonicaSaDvijeStudentice() throws Exception {
		amila = token("amila.rezervacije@example.com", "Amila");
		hana = token("hana.rezervacije@example.com", "Hana");
		lamija = token("lamija.rezervacije@example.com", "Lamija");
		var postojeca = jdbc.queryForList("select id from organizations where slug = 'citaonica-rezervacije-api'",
				UUID.class);
		citaonica = postojeca.isEmpty() ? novaOrganizacija() : postojeca.getFirst();
		for (String email : new String[] { "hana.rezervacije@example.com", "lamija.rezervacije@example.com" }) {
			jdbc.update("insert into organization_members values (?, ?, 'CLIENT', now()) on conflict do nothing",
					citaonica, users.findByEmail(email).orElseThrow().getId());
		}
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
				.header("Authorization", "Bearer " + amila)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Čitaonica FIT", "slug": "citaonica-rezervacije-api", "timezone": "Europe/Sarajevo"}
						""")));
	}

	private UUID idIz(ResultActions odgovor) throws Exception {
		return UUID.fromString(JsonPath.read(odgovor.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), "$.id"));
	}

	// Sto sa satnim terminima, ponedjeljkom od 08 do 12
	private UUID sto() throws Exception {
		return sto(true);
	}

	private UUID sto(boolean autoConfirm) throws Exception {
		return sto(autoConfirm, 0);
	}

	private UUID sto(boolean autoConfirm, int cancelNoticeHours) throws Exception {
		UUID sto = idIz(mockMvc.perform(post("/api/organizations/{id}/resources", citaonica)
				.header("Authorization", "Bearer " + amila)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Sto %s", "slotMinutes": 60, "autoConfirm": %s, "cancelNoticeHours": %d}
						""".formatted(UUID.randomUUID().toString().substring(0, 8), autoConfirm, cancelNoticeHours))));
		mockMvc.perform(put("/api/resources/{id}/availability", sto)
				.header("Authorization", "Bearer " + amila)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"rules": [{"dayOfWeek": "MONDAY", "start": "08:00", "end": "12:00"}]}
						"""))
				.andExpect(status().isOk());
		return sto;
	}

	private ResultActions rezervisi(String token, UUID resurs, String pocetak) throws Exception {
		return mockMvc.perform(zahtjev(token, resurs, pocetak));
	}

	private ResultActions rezervisi(String token, UUID resurs, String pocetak, String kljuc) throws Exception {
		return mockMvc.perform(zahtjev(token, resurs, pocetak).header("Idempotency-Key", kljuc));
	}

	private MockHttpServletRequestBuilder zahtjev(String token, UUID resurs, String pocetak) {
		return post("/api/resources/{id}/bookings", resurs)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"startTime": "%s"}
						""".formatted(pocetak));
	}

	@Test
	void studenticaRezervisePonudjenTerminIDobijaGaUUtc() throws Exception {
		rezervisi(hana, sto(), "2035-11-19T09:00:00+01:00")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.startTime").value("2035-11-19T08:00:00Z"))
				.andExpect(jsonPath("$.endTime").value("2035-11-19T09:00:00Z"))
				.andExpect(jsonPath("$.status").value("CONFIRMED"));
	}

	@Test
	void drugaStudenticaZaIstiTerminDobija409() throws Exception {
		UUID sto = sto();
		rezervisi(hana, sto, "2035-11-19T09:00:00+01:00").andExpect(status().isCreated());

		// Isti trenutak zapisan u UTC-u
		rezervisi(lamija, sto, "2035-11-19T08:00:00Z")
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.title").value("Termin je zauzet"));
	}

	@Test
	void terminKojiSeNeNudiDaje400ZaPoljeStartTime() throws Exception {
		rezervisi(hana, sto(), "2035-11-19T09:30:00+01:00")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.greske[0].polje").value("startTime"));
	}

	@Test
	void pocetakBezPomakaSeOdbija() throws Exception {
		rezervisi(hana, sto(), "2035-11-19T09:00:00")
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"));
	}

	private ResultActions slobodniTermini(String token, UUID resurs) throws Exception {
		return mockMvc.perform(get("/api/resources/{id}/slots", resurs)
				.param("from", "2035-11-19")
				.param("to", "2035-11-20")
				.header("Authorization", "Bearer " + token));
	}

	// Ponedjeljak 08-12 po Sarajevu je 07-11 UTC. Hanin termin u 09:00 (08:00 UTC) više se ne nudi, ni Lamiji.
	@Test
	void rezervisanTerminNestajeIzSlobodnihTermina() throws Exception {
		UUID sto = sto();
		rezervisi(hana, sto, "2035-11-19T09:00:00+01:00").andExpect(status().isCreated());

		slobodniTermini(lamija, sto)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.slots[*].start",
						contains("2035-11-19T07:00:00Z", "2035-11-19T09:00:00Z", "2035-11-19T10:00:00Z")));
	}

	@Test
	void otkazanaRezervacijaNeZauzimaTermin() throws Exception {
		UUID sto = sto();
		jdbc.update("insert into bookings values (gen_random_uuid(), ?, ?, '2035-11-19 08:00Z', '2035-11-19 09:00Z', "
				+ "'CANCELLED', now())", sto, users.findByEmail("hana.rezervacije@example.com").orElseThrow().getId());

		slobodniTermini(lamija, sto)
				.andExpect(jsonPath("$.slots[*].start", contains("2035-11-19T07:00:00Z", "2035-11-19T08:00:00Z",
						"2035-11-19T09:00:00Z", "2035-11-19T10:00:00Z")))
				.andExpect(jsonPath("$.taken", empty()));
	}

	// Kalendar mora razlikovati zauzet termin od moje rezervacije. Lamija vidi samo da je termin
	// zauzet, a ne čiji je ni u kom statusu. Hana vidi da je njen.
	@Test
	void zauzetTerminVideSviAliSamoVlasnicaVidiDaJeNjen() throws Exception {
		UUID sto = sto();
		rezervisi(hana, sto, "2035-11-19T09:00:00+01:00").andExpect(status().isCreated());

		slobodniTermini(lamija, sto)
				.andExpect(jsonPath("$.taken[*].start", contains("2035-11-19T08:00:00Z")))
				.andExpect(jsonPath("$.taken[0].end").value("2035-11-19T09:00:00Z"))
				.andExpect(jsonPath("$.taken[0].myStatus").value(nullValue()));
		slobodniTermini(hana, sto)
				.andExpect(jsonPath("$.taken[*].start", contains("2035-11-19T08:00:00Z")))
				.andExpect(jsonPath("$.taken[0].myStatus").value("CONFIRMED"));
	}

	@Test
	void mojaRezervacijaNaCekanjuJeZauzetaSaStatusomPending() throws Exception {
		UUID sto = sto(false);
		rezervisi(hana, sto, "2035-11-19T11:00:00+01:00").andExpect(jsonPath("$.status").value("PENDING"));

		slobodniTermini(hana, sto)
				.andExpect(jsonPath("$.slots[*].start", not(hasItem("2035-11-19T10:00:00Z"))))
				.andExpect(jsonPath("$.taken[*].start", contains("2035-11-19T10:00:00Z")))
				.andExpect(jsonPath("$.taken[0].myStatus").value("PENDING"));
	}

	// Klijent ne vidi deaktiviran resurs, pa ga ne može ni rezervisati
	@Test
	void klijentNeMozeRezervisatiDeaktiviranResurs() throws Exception {
		UUID sto = sto();
		mockMvc.perform(delete("/api/organizations/{id}/resources/{resourceId}", citaonica, sto)
				.header("Authorization", "Bearer " + amila))
				.andExpect(status().isNoContent());

		rezervisi(hana, sto, "2035-11-19T09:00:00+01:00").andExpect(status().isForbidden());
	}

	@Test
	void ponovljenZahtjevSIstimKljucemVracaIstuRezervacijuIStatus201() throws Exception {
		UUID sto = sto();
		String kljuc = UUID.randomUUID().toString();
		String prvi = rezervisi(hana, sto, "2035-11-19T09:00:00+01:00", kljuc)
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		rezervisi(hana, sto, "2035-11-19T09:00:00+01:00", kljuc)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(JsonPath.<String>read(prvi, "$.id")));
	}

	@Test
	void istiKljucZaDrugiTerminDaje422() throws Exception {
		UUID sto = sto();
		String kljuc = UUID.randomUUID().toString();
		rezervisi(hana, sto, "2035-11-19T09:00:00+01:00", kljuc).andExpect(status().isCreated());

		rezervisi(hana, sto, "2035-11-19T10:00:00+01:00", kljuc)
				.andExpect(status().isUnprocessableContent())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.title").value("Ključ je već iskorišten"));
	}

	@Test
	void predugKljucDaje400ZaZaglavlje() throws Exception {
		rezervisi(hana, sto(), "2035-11-19T09:00:00+01:00", "k".repeat(101))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.greske[0].polje").value("Idempotency-Key"));
	}

	private String idIzOdgovora(ResultActions odgovor) throws Exception {
		return JsonPath.read(odgovor.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
				"$.id");
	}

	// Hana vidi svoje rezervacije iz svih resursa, sa nazivom i zonom, a Lamijinu ne
	@Test
	void mojeRezervacijeSuSamoMoje() throws Exception {
		UUID sto = sto();
		String hanina = idIzOdgovora(rezervisi(hana, sto, "2035-11-19T09:00:00+01:00"));
		String lamijina = idIzOdgovora(rezervisi(lamija, sto, "2035-11-19T10:00:00+01:00"));

		mockMvc.perform(get("/api/bookings/mine").param("size", "100")
				.header("Authorization", "Bearer " + hana))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].id", hasItem(hanina)))
				.andExpect(jsonPath("$.content[*].id", not(hasItem(lamijina))))
				.andExpect(jsonPath("$.content[?(@.id == '%s')].timezone".formatted(hanina)).value("Europe/Sarajevo"))
				.andExpect(jsonPath("$.content[?(@.id == '%s')].resourceName".formatted(hanina))
						.value(hasItem(startsWith("Sto "))));
	}

	// Termin u 09:00 po Sarajevu (08:00 UTC), otkazivanje najkasnije 24 h prije. Frontend iz roka
	// zna da li da ponudi otkazivanje, a iz naziva organizacije čija je sala u listi preko svih organizacija.
	@Test
	void mojaRezervacijaImaRokZaOtkazivanjeINazivOrganizacije() throws Exception {
		UUID sto = sto(true, 24);
		String hanina = idIzOdgovora(rezervisi(hana, sto, "2035-11-19T09:00:00+01:00"));

		mockMvc.perform(get("/api/bookings/mine").param("size", "100")
				.header("Authorization", "Bearer " + hana))
				.andExpect(jsonPath("$.content[?(@.id == '%s')].cancelDeadline".formatted(hanina))
						.value(hasItem("2035-11-18T08:00:00Z")))
				.andExpect(jsonPath("$.content[?(@.id == '%s')].organizationName".formatted(hanina))
						.value(hasItem("Čitaonica FIT")));
	}
}
