package ba.slotbook.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import ba.slotbook.TestcontainersConfiguration;

// "default" uz "demo", da se učita i application-default iz testova (bez slanja mailova, BCrypt 4)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "default", "demo" })
class DemoApiTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	DemoPodaci podaci;

	@Autowired
	DataSource dataSource;

	// Svaki test kreće od svježeg dema: računi se "postare" za dva dana, pa provjera mora napuniti
	@BeforeEach
	void svjezDemo() {
		jdbc.update("update users set created_at = created_at - interval '2 days'");
		assertThat(podaci.osvjeziAkoTreba()).isTrue();
	}

	@Test
	void prijavaDobijaDemoRacuneIJavnuLozinku() throws Exception {
		mockMvc.perform(get("/api/demo"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lozinka").value(DemoPodaci.LOZINKA))
				.andExpect(jsonPath("$.racuni[*].email").value(contains("klijent@slotbook.test",
						"provider@slotbook.test", "admin@slotbook.test")));
		for (DemoRacun racun : DemoPodaci.RACUNI) {
			assertThat(token(racun.email())).isNotBlank();
		}
	}

	@Test
	void klijentImaKarteUSvimStanjima() throws Exception {
		mockMvc.perform(get("/api/bookings/mine").param("size", "50")
				.header("Authorization", "Bearer " + token("klijent@slotbook.test")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].status").value(hasItems("CONFIRMED", "PENDING", "COMPLETED",
						"CANCELLED")))
				.andExpect(jsonPath("$.content[*].organizationName").value(hasItems("Čitaonica PMF",
						"Teretana Ilidža")));
	}

	// Link iz dema mora stvarno raditi: adresu koju admin vidi, pregled prihvata kao upotrebljivu
	@Test
	void adminVidiPozivniLinkKojiRadiIPozivnicuKojaCeka() throws Exception {
		UUID citaonica = jdbc.queryForObject("select id from organizations where slug = 'citaonica-pmf'", UUID.class);
		String admin = token("admin@slotbook.test");
		String linkovi = mockMvc.perform(get("/api/organizations/{id}/invite-links", citaonica)
				.header("Authorization", "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].uses").value(7))
				.andReturn().getResponse().getContentAsString();
		String url = JsonPath.read(linkovi, "$.content[0].url");

		mockMvc.perform(get("/api/invite-links/{t}", url.substring(url.lastIndexOf('/') + 1)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.usable").value(true));
		mockMvc.perform(get("/api/organizations/{id}/invites", citaonica).header("Authorization", "Bearer " + admin))
				.andExpect(jsonPath("$.content[0].role").value("PROVIDER"));
	}

	@Test
	void adminVidiStatistikuZaProsleCetiriSedmice() throws Exception {
		UUID citaonica = jdbc.queryForObject("select id from organizations where slug = 'citaonica-pmf'", UUID.class);
		LocalDate danas = LocalDate.now(ZoneId.of("Europe/Sarajevo"));
		mockMvc.perform(get("/api/organizations/{id}/stats", citaonica)
				.param("from", danas.minusDays(28).toString())
				.param("to", danas.toString())
				.header("Authorization", "Bearer " + token("admin@slotbook.test")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.byStatus.COMPLETED").value(greaterThan(50)))
				.andExpect(jsonPath("$.byStatus.CANCELLED").value(greaterThan(0)))
				.andExpect(jsonPath("$.byStatus.NO_SHOW").value(greaterThan(0)));
	}

	@Test
	void svjezDemoOstajeSPodacimaPosjetilaca() throws Exception {
		organizacijaPosjetioca();

		assertThat(podaci.osvjeziAkoTreba()).isFalse();
		assertThat(brojOrganizacija()).isEqualTo(3);
	}

	@Test
	void demoStarijiOdDanaSeVracaNaPocetak() throws Exception {
		organizacijaPosjetioca();
		jdbc.update("update users set created_at = created_at - interval '25 hours'");

		assertThat(podaci.osvjeziAkoTreba()).isTrue();
		assertThat(brojOrganizacija()).isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(9);
		assertThat(token("klijent@slotbook.test")).isNotBlank();
	}

	@Test
	void obrisanDemoRacunVracaDemoOdmah() throws Exception {
		mockMvc.perform(delete("/api/me")
				.header("Authorization", "Bearer " + token("klijent@slotbook.test"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"password": "%s"}
						""".formatted(DemoPodaci.LOZINKA)))
				.andExpect(status().isNoContent());

		assertThat(podaci.osvjeziAkoTreba()).isTrue();
		assertThat(token("klijent@slotbook.test")).isNotBlank();
	}

	private void organizacijaPosjetioca() throws Exception {
		mockMvc.perform(post("/api/organizations")
				.header("Authorization", "Bearer " + token("admin@slotbook.test"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Sala za sastanke", "slug": "sala-posjetioca", "timezone": "Europe/Sarajevo"}
						"""))
				.andExpect(status().isCreated());
	}

	private int brojOrganizacija() {
		return jdbc.queryForObject("select count(*) from organizations", Integer.class);
	}

	// CI 25.11.2026: TRUNCATE iz vraćanja dema se sudario s pozadinskim poslom i Postgres ga je prekinuo
	// (deadlock detected). Ovdje se isti sudar pravi namjerno: druga transakcija drži zadnju tabelu iz
	// TRUNCATE-a, a kad TRUNCATE čeka na nju, čita prvu, koju je TRUNCATE već zaključao.
	@Test
	void vracanjeDemaUspijeIKadIzgubiUDeadlockuSDrugomTransakcijom() throws Exception {
		jdbc.update("update users set created_at = created_at - interval '2 days'");
		long deadlockaPrije = deadlockova();
		try (Connection druga = dataSource.getConnection(); Statement sql = druga.createStatement()) {
			druga.setAutoCommit(false);
			// Druga transakcija ne smije biti žrtva: Postgres prekida onu koja prva otkrije krug, a to
			// je ona kojoj prije istekne deadlock_timeout (reset ima podrazumijevanu 1 s)
			sql.execute("set local deadlock_timeout = '10s'");
			sql.execute("set local statement_timeout = '20s'");
			sql.execute("lock table waitlist_entries in access share mode");

			CompletableFuture<Boolean> vracanje = CompletableFuture.supplyAsync(podaci::osvjeziAkoTreba);
			cekajDaTruncateCekaNaZakljucavanje();
			sql.executeQuery("select count(*) from audit_log").close();
			druga.commit();

			assertThat(vracanje.get(30, TimeUnit.SECONDS)).isTrue();
		}
		// Bez ovoga bi test prošao i kad do sudara uopšte ne dođe
		assertThat(cekajNoviDeadlock(deadlockaPrije)).as("Postgres je zabilježio deadlock").isTrue();
		assertThat(jdbc.queryForObject("select count(*) from users where email = ?", Integer.class,
				"klijent@slotbook.test")).isEqualTo(1);
	}

	private void cekajDaTruncateCekaNaZakljucavanje() throws InterruptedException {
		for (int i = 0; i < 200; i++) {
			Integer cekaju = jdbc.queryForObject("""
					select count(*) from pg_stat_activity
					where wait_event_type = 'Lock' and query ilike 'truncate%'
					""", Integer.class);
			if (cekaju > 0) {
				return;
			}
			Thread.sleep(50);
		}
		throw new AssertionError("TRUNCATE nije počeo čekati na zaključanu tabelu");
	}

	// Brojač u pg_stat_database se ne osvježi istog trena, nego kad prekinuta sesija završi transakciju
	private boolean cekajNoviDeadlock(long prije) throws InterruptedException {
		for (int i = 0; i < 100; i++) {
			if (deadlockova() > prije) {
				return true;
			}
			Thread.sleep(200);
		}
		return false;
	}

	private long deadlockova() {
		return jdbc.queryForObject("select deadlocks from pg_stat_database where datname = current_database()",
				Long.class);
	}

	private String token(String email) throws Exception {
		String tijelo = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "%s"}
				""".formatted(email, DemoPodaci.LOZINKA)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(tijelo, "$.accessToken");
	}

}
