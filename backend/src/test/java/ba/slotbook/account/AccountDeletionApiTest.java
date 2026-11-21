package ba.slotbook.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import ba.slotbook.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AccountDeletionApiTest {

	private static final String LOZINKA = "hum-iznad-mostara";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	private record Prijava(String access, String refresh) {
	}

	private ResultActions posalji(MockHttpServletRequestBuilder zahtjev, String token, String tijelo) throws Exception {
		if (token != null) {
			zahtjev.header("Authorization", "Bearer " + token);
		}
		if (tijelo != null) {
			zahtjev.contentType(MediaType.APPLICATION_JSON).content(tijelo);
		}
		return mockMvc.perform(zahtjev);
	}

	private Prijava prijava(String email) throws Exception {
		MockHttpServletResponse odgovor = posalji(post("/api/auth/login"), null, """
				{"email": "%s", "password": "%s"}
				""".formatted(email, LOZINKA)).andExpect(status().isOk()).andReturn().getResponse();
		return new Prijava(JsonPath.read(odgovor.getContentAsString(), "$.accessToken"),
				odgovor.getCookie("refresh_token").getValue());
	}

	private Prijava nalog(String email, String ime) throws Exception {
		posalji(post("/api/auth/register"), null, """
				{"email": "%s", "password": "%s", "displayName": "%s"}
				""".formatted(email, LOZINKA, ime)).andExpect(status().isAccepted());
		jdbc.update("update users set email_verified_at = now() where email = ?", email);
		return prijava(email);
	}

	private UUID organizacija(String token, String slug) throws Exception {
		String tijelo = posalji(post("/api/organizations"), token, """
				{"name": "Teretana %s", "slug": "%s", "timezone": "Europe/Sarajevo"}
				""".formatted(slug, slug)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(tijelo, "$.id"));
	}

	private UUID resurs(String token, UUID organizacija, String naziv) throws Exception {
		String tijelo = posalji(post("/api/organizations/{id}/resources", organizacija), token, """
				{"name": "%s", "slotMinutes": 60}
				""".formatted(naziv)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(tijelo, "$.id"));
	}

	private void clan(UUID organizacija, String email, String uloga) {
		jdbc.update("insert into organization_members select ?, id, ?, now() from users where email = ?",
				organizacija, uloga, email);
	}

	private UUID rezervacija(UUID resurs, String email, String pocetak, String status) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into bookings (id, resource_id, client_id, start_time, end_time, status, created_at) "
				+ "select ?, ?, id, ?::timestamptz, ?::timestamptz + interval '1 hour', ?, now() from users "
				+ "where email = ?", id, resurs, pocetak, pocetak, status, email);
		return id;
	}

	private ResultActions obrisi(String token, String lozinka) throws Exception {
		return posalji(delete("/api/me"), token, """
				{"password": "%s"}
				""".formatted(lozinka));
	}

	private long broj(String sql, Object... parametri) {
		return jdbc.queryForObject(sql, Long.class, parametri);
	}

	@Test
	void brisanjeAnonimiziraNalogGasiSesijeIOtkazujeBuduceTermine() throws Exception {
		String email = "selma.brisanje@example.com";
		Prijava damir = nalog("damir.brisanje@example.com", "Damir");
		UUID teretana = organizacija(damir.access(), "grbavica-brisanje");
		UUID joga = resurs(damir.access(), teretana, "Sala za jogu");
		Prijava selma = nalog(email, "Selma");
		posalji(post("/api/organizations/{id}/invites", teretana), damir.access(), """
				{"email": "%s", "role": "PROVIDER"}
				""".formatted(email)).andExpect(status().isCreated());
		clan(teretana, email, "CLIENT");
		UUID buduca = rezervacija(joga, email, "2035-11-19T08:00:00Z", "CONFIRMED");
		// Prošla, a još potvrđena: BookingJob je zatvara tek dan poslije kraja
		UUID prosla = rezervacija(joga, email, "2025-11-19T08:00:00Z", "CONFIRMED");
		posalji(post("/api/auth/password-reset"), null, """
				{"email": "%s"}
				""".formatted(email)).andExpect(status().isNoContent());
		posalji(post("/api/auth/login"), null, """
				{"email": "%s", "password": "pogresna"}
				""".formatted(email)).andExpect(status().isUnauthorized());
		UUID id = jdbc.queryForObject("select id from users where email = ?", UUID.class, email);

		obrisi(selma.access(), LOZINKA).andExpect(status().isNoContent());

		// Red ostaje, da rezervacije i statistika ostanu tačne, ali bez ičega što vodi do osobe
		assertThat(jdbc.queryForMap("select email, display_name, password_hash, email_verified_at, deleted_at "
				+ "from users where id = ?", id))
				.containsEntry("email", "obrisan-" + id + "@slotbook.invalid")
				.containsEntry("display_name", "Obrisan nalog")
				.containsEntry("password_hash", "!")
				.containsEntry("email_verified_at", null)
				.extractingByKey("deleted_at").isNotNull();

		assertThat(jdbc.queryForObject("select status from bookings where id = ?", String.class, buduca))
				.isEqualTo("CANCELLED");
		assertThat(broj("select count(*) from booking_events where booking_id = ? and to_status = 'CANCELLED' "
				+ "and actor_id = ?", buduca, id)).isEqualTo(1);
		assertThat(jdbc.queryForMap("select status, client_id from bookings where id = ?", prosla))
				.containsEntry("status", "CONFIRMED").containsEntry("client_id", id);

		assertThat(broj("select count(*) from organization_members where user_id = ?", id)).isZero();
		assertThat(broj("select count(*) from notifications where user_id = ? or email = ?", id, email)).isZero();
		assertThat(broj("select count(*) from user_tokens where user_id = ?", id)).isZero();
		assertThat(broj("select count(*) from organization_invites where email = ?", email)).isZero();
		assertThat(broj("select count(*) from login_failures where email = ?", email)).isZero();
		assertThat(broj("select count(*) from refresh_tokens where user_id = ? and revoked_at is null", id)).isZero();
		assertThat(jdbc.queryForList("select action from audit_log where organization_id = ? and actor_id = ?",
				String.class, teretana, id)).containsExactly("ACCOUNT_DELETED");

		// Na kraju, jer svaka pogrešna prijava, i za nepoznat email, opet upiše red u login_failures
		posalji(post("/api/auth/login"), null, """
				{"email": "%s", "password": "%s"}
				""".formatted(email, LOZINKA)).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", selma.refresh())))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void pogresnaLozinkaNistaNeBriseIBrojiSeKaoGreskaPrijave() throws Exception {
		String email = "nedim.brisanje@example.com";
		Prijava nedim = nalog(email, "Nedim");

		obrisi(nedim.access(), "nije-moja-lozinka")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.greske[*].polje", contains("password")));

		assertThat(broj("select count(*) from users where email = ? and deleted_at is null", email)).isEqualTo(1);
		assertThat(broj("select failures from login_failures where email = ?", email)).isEqualTo(1);
	}

	@Test
	void jediniAdminDobija409SNazivomOrganizacijeADrugiAdminMozeObrisati() throws Exception {
		Prijava lamija = nalog("lamija.brisanje@example.com", "Lamija");
		nalog("haris.brisanje@example.com", "Haris");
		UUID citaonica = organizacija(lamija.access(), "citaonica-brisanje");

		obrisi(lamija.access(), LOZINKA)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.organizacije", contains("Teretana citaonica-brisanje")));

		clan(citaonica, "haris.brisanje@example.com", "ADMIN");
		obrisi(lamija.access(), LOZINKA).andExpect(status().isNoContent());
		assertThat(broj("select count(*) from organization_members where organization_id = ? and role = 'ADMIN'",
				citaonica)).isEqualTo(1);
	}

	// Strani ključ resources_provider_clan veže resurs za članstvo, i neaktivan resurs
	@Test
	void providerKojiVodiResursDobija409DokGaAdminNePrebaci() throws Exception {
		Prijava amel = nalog("amel.brisanje@example.com", "Amel");
		Prijava mersiha = nalog("mersiha.brisanje@example.com", "Mersiha");
		UUID sala = organizacija(amel.access(), "sala-brisanje");
		clan(sala, "mersiha.brisanje@example.com", "PROVIDER");
		UUID bazen = resurs(mersiha.access(), sala, "Bazen, staza 2");
		posalji(delete("/api/organizations/{id}/resources/{r}", sala, bazen), mersiha.access(), null)
				.andExpect(status().isNoContent());

		obrisi(mersiha.access(), LOZINKA)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.resursi", contains("Bazen, staza 2")));

		UUID amelId = jdbc.queryForObject("select id from users where email = 'amel.brisanje@example.com'",
				UUID.class);
		posalji(put("/api/organizations/{id}/resources/{r}/provider", sala, bazen), amel.access(), """
				{"providerId": "%s"}
				""".formatted(amelId)).andExpect(status().isOk());
		obrisi(mersiha.access(), LOZINKA).andExpect(status().isNoContent());
	}

	@Test
	void istiEmailSePosliBrisanjaMozePonovoRegistrovati() throws Exception {
		String email = "irma.brisanje@example.com";
		Prijava irma = nalog(email, "Irma");
		obrisi(irma.access(), LOZINKA).andExpect(status().isNoContent());

		posalji(post("/api/auth/register"), null, """
				{"email": "%s", "password": "%s", "displayName": "Irma"}
				""".formatted(email, LOZINKA)).andExpect(status().isAccepted());

		assertThat(broj("select count(*) from users where email = ? and deleted_at is null", email)).isEqualTo(1);
	}

	@Test
	void bezPrijaveNemaBrisanja() throws Exception {
		obrisi(null, LOZINKA).andExpect(status().isUnauthorized());
	}

	// Dva admina iste organizacije brišu nalog u istom trenutku. Bez zaključavanja oba vide drugog admina
	// i organizacija ostane bez ijednog.
	@Test
	void dvaAdminaIstovremenoOrganizacijaNikadNeOstajeBezAdmina() throws Exception {
		for (int krug = 0; krug < 5; krug++) {
			Prijava prvi = nalog("edin.istovremeno-brisanje" + krug + "@example.com", "Edin");
			Prijava drugi = nalog("vedad.istovremeno-brisanje" + krug + "@example.com", "Vedad");
			UUID org = organizacija(prvi.access(), "istovremeno-brisanje-" + krug);
			clan(org, "vedad.istovremeno-brisanje" + krug + "@example.com", "ADMIN");

			CountDownLatch start = new CountDownLatch(1);
			List<Future<Integer>> statusi = new ArrayList<>();
			try (ExecutorService niti = Executors.newFixedThreadPool(2)) {
				for (Prijava admin : List.of(prvi, drugi)) {
					statusi.add(niti.submit(() -> {
						start.await();
						return obrisi(admin.access(), LOZINKA).andReturn().getResponse().getStatus();
					}));
				}
				start.countDown();
				List<Integer> ishodi = new ArrayList<>();
				for (Future<Integer> status : statusi) {
					ishodi.add(status.get());
				}
				assertThat(ishodi).as("krug %d", krug).containsExactlyInAnyOrder(204, 409);
			}
			assertThat(broj("select count(*) from organization_members where organization_id = ? and role = 'ADMIN'",
					org)).as("krug %d", krug).isEqualTo(1);
		}
	}

}
