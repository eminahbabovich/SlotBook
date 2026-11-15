package ba.slotbook.stats;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.user.UserRepository;

// Kriterij Faze 6: brojke iz API-ja se poklapaju s ručno izračunatim iz podataka ispod.
// Novembar 2035. u čitaonici, cijeli mjesec je po zimskom vremenu (+01).
//
// Sto br. 1 (Selma), ponedjeljkom 08-12: četiri ponedjeljka po 4 satna termina = 960 minuta,
// minus izuzetak 12.11. od 08 do 10 = 840 ponuđenih. Zauzeto: dvije potvrđene, jedna završena i
// jedan nedolazak = 240. Otkazana i ona na čekanju ne zauzimaju, a ona od 3.12. je van perioda.
// Sto br. 2 (Lejla), utorkom 14-16: četiri utorka po 2 termina = 480 ponuđenih, zauzeto 60.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class StatsApiTest {

	private static final String LOZINKA = "stari-most-1566";
	private static final String NOVEMBAR = "?from=2035-11-01&to=2035-12-01";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	UserRepository users;

	private String lejla;
	private String selma;
	private String emina;
	private UUID citaonica;
	private UUID stoBr1;
	private UUID stoBr2;

	// Podaci se upišu samo prvi put, jer se kontekst i baza dijele s drugim testovima, a ovi samo čitaju
	@BeforeEach
	void citaonicaZaNovembar() throws Exception {
		lejla = token("lejla.statistika@example.com", "Lejla");
		selma = token("selma.statistika@example.com", "Selma");
		emina = token("emina.statistika@example.com", "Emina");
		var postojeca = jdbc.queryForList("select id from organizations where slug = 'citaonica-statistika'",
				UUID.class);
		if (postojeca.isEmpty()) {
			citaonica = novaCitaonica();
			podaciZaNovembar();
		} else {
			citaonica = postojeca.getFirst();
		}
		stoBr1 = resurs("Sto br. 1");
		stoBr2 = resurs("Sto br. 2");
	}

	@Test
	void adminVidiCijeluCitaonicuIBrojkeSePoklapajuSRucnoIzracunatim() throws Exception {
		mockMvc.perform(get("/api/organizations/{id}/stats" + NOVEMBAR, citaonica)
				.header("Authorization", "Bearer " + lejla))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{
							"from": "2035-11-01", "to": "2035-12-01", "timezone": "Europe/Sarajevo",
							"byStatus": {"PENDING": 1, "CONFIRMED": 2, "REJECTED": 0, "EXPIRED": 0,
								"CANCELLED": 1, "COMPLETED": 2, "NO_SHOW": 1},
							"byDayAndHour": [
								{"dayOfWeek": 1, "hour": 8, "count": 3},
								{"dayOfWeek": 1, "hour": 9, "count": 1},
								{"dayOfWeek": 1, "hour": 10, "count": 1},
								{"dayOfWeek": 1, "hour": 11, "count": 1},
								{"dayOfWeek": 2, "hour": 14, "count": 1}],
							"occupancy": [
								{"resourceId": "%s", "name": "Sto br. 1", "bookedMinutes": 240, "offeredMinutes": 840},
								{"resourceId": "%s", "name": "Sto br. 2", "bookedMinutes": 60, "offeredMinutes": 480}]
						}
						""".formatted(stoBr1, stoBr2), JsonCompareMode.STRICT));
	}

	// Selma vodi samo Sto br. 1, pa nema Lejlinog utorka ni drugog stola
	@Test
	void providerVidiSamoSvojSto() throws Exception {
		mockMvc.perform(get("/api/organizations/{id}/stats" + NOVEMBAR, citaonica)
				.header("Authorization", "Bearer " + selma))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.byStatus.COMPLETED").value(1))
				.andExpect(jsonPath("$.byDayAndHour.length()").value(4))
				.andExpect(jsonPath("$.occupancy.length()").value(1))
				.andExpect(jsonPath("$.occupancy[0].resourceId").value(stoBr1.toString()))
				.andExpect(jsonPath("$.occupancy[0].bookedMinutes").value(240));
	}

	@Test
	void klijentNeVidiStatistiku() throws Exception {
		mockMvc.perform(get("/api/organizations/{id}/stats" + NOVEMBAR, citaonica)
				.header("Authorization", "Bearer " + emina))
				.andExpect(status().isForbidden());
	}

	// 1.1.2035. do 3.1.2036. je 367 dana
	@Test
	void periodDuziOdGodineSeOdbija() throws Exception {
		mockMvc.perform(get("/api/organizations/{id}/stats?from=2035-01-01&to=2036-01-03", citaonica)
				.header("Authorization", "Bearer " + lejla))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.greske[0].polje").value("to"));
	}

	@Test
	void csvSaPopunjenoscuSePreuzimaKaoFajl() throws Exception {
		mockMvc.perform(get("/api/organizations/{id}/stats" + NOVEMBAR, citaonica)
				.header("Authorization", "Bearer " + lejla)
				.accept("text/csv"))
				.andExpect(status().isOk())
				.andExpect(content().contentType("text/csv;charset=UTF-8"))
				.andExpect(header().string("Content-Disposition",
						"attachment; filename=\"popunjenost-2035-11-01-2035-12-01.csv\""))
				.andExpect(content().string("﻿Resurs;Zauzeto (min);Ponuđeno (min);Popunjenost (%)\r\n"
						+ "Sto br. 1;240;840;29\r\n"
						+ "Sto br. 2;60;480;13\r\n"));
	}

	// CSV ima svoju metodu u kontroleru, pa i svoj @PreAuthorize koji treba provjeriti
	@Test
	void klijentNeMozePreuzetiNiCsv() throws Exception {
		mockMvc.perform(get("/api/organizations/{id}/stats" + NOVEMBAR, citaonica)
				.header("Authorization", "Bearer " + emina)
				.accept("text/csv"))
				.andExpect(status().isForbidden());
	}

	// Test izolacije šalje zahtjev bez Accept i pogodi JSON metodu, pa se CSV za tuđu organizaciju
	// provjerava ovdje. Bojan je admin, ali svoje teretane.
	@Test
	void adminDrugeOrganizacijeNeMozePreuzetiCsv() throws Exception {
		String bojan = token("bojan.statistika@example.com", "Bojan");
		if (jdbc.queryForList("select id from organizations where slug = 'teretana-statistika'").isEmpty()) {
			mockMvc.perform(post("/api/organizations")
					.header("Authorization", "Bearer " + bojan)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"name": "Teretana za statistiku", "slug": "teretana-statistika", "timezone": "Europe/Sarajevo"}
							"""))
					.andExpect(status().isCreated());
		}

		mockMvc.perform(get("/api/organizations/{id}/stats" + NOVEMBAR, citaonica)
				.header("Authorization", "Bearer " + bojan)
				.accept("text/csv"))
				.andExpect(status().isForbidden());
	}

	// Bez zaglavlja Accept (curl, stari klijent) i dalje stiže JSON, a ne fajl
	@Test
	void bezAcceptZaglavljaStizeJson() throws Exception {
		mockMvc.perform(get("/api/organizations/{id}/stats" + NOVEMBAR, citaonica)
				.header("Authorization", "Bearer " + lejla))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
	}

	// Klijent je tražio CSV, a greška je Problem Details u JSON-u. Treba ostati 400, ne 406.
	@Test
	void neispravanPeriodZaCsvIDaljeDaje400() throws Exception {
		mockMvc.perform(get("/api/organizations/{id}/stats?from=2035-01-01&to=2036-01-03", citaonica)
				.header("Authorization", "Bearer " + lejla)
				.accept("text/csv"))
				.andExpect(status().isBadRequest());
	}

	private UUID novaCitaonica() throws Exception {
		String tijelo = mockMvc.perform(post("/api/organizations")
				.header("Authorization", "Bearer " + lejla)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Čitaonica za statistiku", "slug": "citaonica-statistika", "timezone": "Europe/Sarajevo"}
						"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(tijelo, "$.id"));
	}

	private void podaciZaNovembar() {
		UUID selmaId = id("selma.statistika@example.com");
		jdbc.update("insert into organization_members values (?, ?, 'PROVIDER', now())", citaonica, selmaId);
		jdbc.update("insert into organization_members values (?, ?, 'CLIENT', now())", citaonica,
				id("emina.statistika@example.com"));
		UUID sto1 = noviResurs("Sto br. 1", selmaId);
		UUID sto2 = noviResurs("Sto br. 2", id("lejla.statistika@example.com"));
		jdbc.update("insert into availability_rules values (gen_random_uuid(), ?, 1, '08:00', '12:00')", sto1);
		jdbc.update("insert into availability_rules values (gen_random_uuid(), ?, 2, '14:00', '16:00')", sto2);
		jdbc.update("insert into time_off values (gen_random_uuid(), ?, '2035-11-12 08:00+01', '2035-11-12 10:00+01', "
				+ "'Krečenje', now())", sto1);

		rezervacija(sto1, "2035-11-05 08:00+01", "CONFIRMED");
		rezervacija(sto1, "2035-11-05 09:00+01", "COMPLETED");
		rezervacija(sto1, "2035-11-12 10:00+01", "NO_SHOW");
		rezervacija(sto1, "2035-11-19 08:00+01", "CANCELLED");
		// Isti termin, uzet ponovo poslije otkazivanja
		rezervacija(sto1, "2035-11-19 08:00+01", "CONFIRMED");
		rezervacija(sto1, "2035-11-26 11:00+01", "PENDING");
		rezervacija(sto1, "2035-12-03 08:00+01", "CONFIRMED");
		rezervacija(sto2, "2035-11-06 14:00+01", "COMPLETED");
	}

	private UUID noviResurs(String naziv, UUID provider) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into resources (id, organization_id, provider_id, name, slot_minutes, created_at) "
				+ "values (?, ?, ?, ?, 60, now())", id, citaonica, provider, naziv);
		return id;
	}

	private UUID resurs(String naziv) {
		return jdbc.queryForObject("select id from resources where organization_id = ? and name = ?", UUID.class,
				citaonica, naziv);
	}

	private void rezervacija(UUID resurs, String pocetak, String status) {
		jdbc.update("insert into bookings (id, resource_id, client_id, start_time, end_time, status, created_at, "
				+ "expires_at) values (gen_random_uuid(), ?, ?, ?::timestamptz, ?::timestamptz + interval '1 hour', "
				+ "?, now(), ?::timestamptz)", resurs, id("emina.statistika@example.com"), pocetak, pocetak, status,
				pocetak);
	}

	private UUID id(String email) {
		return users.findByEmail(email).orElseThrow().getId();
	}

	private String token(String email, String ime) throws Exception {
		if (users.findByEmail(email).isEmpty()) {
			mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
					{"email": "%s", "password": "%s", "displayName": "%s"}
					""".formatted(email, LOZINKA, ime))).andExpect(status().isCreated());
		}
		String tijelo = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "%s"}
				""".formatted(email, LOZINKA))).andReturn().getResponse().getContentAsString();
		return JsonPath.read(tijelo, "$.accessToken");
	}

}
