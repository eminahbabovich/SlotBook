package ba.slotbook.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.UriComponentsBuilder;

import com.jayway.jsonpath.JsonPath;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.user.UserRepository;

// Kriterij "gotovo" Faze 2: korisnik iz organizacije A ne može dotaknuti podatke organizacije B
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OrganizationIsolationApiTest {

	private static final String LOZINKA = "kozija-cuprija-1580";

	// Svaki endpoint nad podacima jedne organizacije. Tijelo mora biti ispravno: @Valid se izvrši
	// prije @PreAuthorize, pa bi neispravno tijelo dalo 400 i sakrilo da autorizacija fali.
	private record Zahtjev(HttpMethod metoda, String putanja, String tijelo) {

		String ruta() {
			return metoda.name() + " " + putanja;
		}

	}

	private static final List<Zahtjev> ZAHTJEVI = List.of(
			new Zahtjev(HttpMethod.GET, "/api/organizations/{id}/members", null),
			new Zahtjev(HttpMethod.POST, "/api/organizations/{id}/invites", """
					{"email": "upad.izolacija@example.com", "role": "ADMIN"}
					"""),
			new Zahtjev(HttpMethod.GET, "/api/organizations/{id}/resources", null),
			new Zahtjev(HttpMethod.POST, "/api/organizations/{id}/resources", """
					{"name": "Sto 7 u čitaonici", "slotMinutes": 120}
					"""),
			new Zahtjev(HttpMethod.PUT, "/api/organizations/{id}/resources/{resourceId}", """
					{"name": "Sto za izolaciju", "slotMinutes": 90}
					"""),
			new Zahtjev(HttpMethod.DELETE, "/api/organizations/{id}/resources/{resourceId}", null),
			new Zahtjev(HttpMethod.PUT, "/api/organizations/{id}/resources/{resourceId}/provider", """
					{"providerId": "{almaId}"}
					"""),
			new Zahtjev(HttpMethod.GET, "/api/resources/{resourceId}", null),
			new Zahtjev(HttpMethod.GET, "/api/resources/{resourceId}/availability", null),
			new Zahtjev(HttpMethod.PUT, "/api/resources/{resourceId}/availability", """
					{"rules": [{"dayOfWeek": "MONDAY", "start": "08:00", "end": "20:00"}]}
					"""),
			new Zahtjev(HttpMethod.GET, "/api/resources/{resourceId}/time-off", null),
			new Zahtjev(HttpMethod.POST, "/api/resources/{resourceId}/time-off", """
					{"startsAt": "2035-08-01T00:00:00+02:00", "endsAt": "2035-08-02T00:00:00+02:00"}
					"""),
			new Zahtjev(HttpMethod.DELETE, "/api/resources/{resourceId}/time-off/{timeOffId}", null));

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository users;

	@Autowired
	OrganizationRepository organizations;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	RequestMappingHandlerMapping rute;

	private String alma;
	private String bojan;
	private UUID citaonica;
	private UUID stoCitaonice;
	private UUID odmorStola;

	// Bojan je admin, ali svoje teretane. Provjera "admin bilo gdje" bi ga pustila, običan član ne bi to otkrio.
	@BeforeEach
	void almaJeAdminCitaoniceBojanAdminTeretane() throws Exception {
		alma = token("alma.izolacija@example.com", "Alma");
		bojan = token("bojan.izolacija@example.com", "Bojan");
		citaonica = organizacija(alma, "citaonica-izolacija");
		organizacija(bojan, "teretana-izolacija");
		stoCitaonice = stoCitaonice();
		odmorStola = odmorStola();
	}

	// Almin DELETE ga obriše, pa se pravi ponovo prije svakog testa
	private UUID odmorStola() {
		var postojeci = jdbc.queryForList("select id from time_off where resource_id = ?", UUID.class, stoCitaonice);
		if (!postojeci.isEmpty()) {
			return postojeci.getFirst();
		}
		UUID id = UUID.randomUUID();
		jdbc.update("insert into time_off values (?, ?, '2035-06-01 00:00+02', '2035-06-02 00:00+02', 'Krečenje', now())",
				id, stoCitaonice);
		return id;
	}

	// Trajanje i aktivnost se vraćaju prije svakog testa, da se vidi da tuđi PUT i DELETE nisu ništa promijenili
	private UUID stoCitaonice() throws Exception {
		var postojeci = jdbc.queryForList("select id from resources where organization_id = ? and name = ?",
				UUID.class, citaonica, "Sto za izolaciju");
		if (!postojeci.isEmpty()) {
			jdbc.update("update resources set slot_minutes = 120, active = true where id = ?", postojeci.getFirst());
			return postojeci.getFirst();
		}
		String tijelo = mockMvc.perform(post("/api/organizations/{id}/resources", citaonica)
				.header("Authorization", "Bearer " + alma)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Sto za izolaciju", "slotMinutes": 120}
						"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(tijelo, "$.id"));
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

	private UUID organizacija(String token, String slug) throws Exception {
		var postojeca = organizations.findBySlug(slug);
		if (postojeca.isPresent()) {
			return postojeca.get().getId();
		}
		String tijelo = mockMvc.perform(post("/api/organizations")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Organizacija %s", "slug": "%s", "timezone": "Europe/Sarajevo"}
						""".formatted(slug, slug)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(tijelo, "$.id"));
	}

	private int posalji(Zahtjev zahtjev, String token) throws Exception {
		// Varijable po imenu, ne po redu: /api/resources/{resourceId} nema {id} organizacije na prvom mjestu
		URI adresa = UriComponentsBuilder.fromUriString(zahtjev.putanja())
				.buildAndExpand(Map.of("id", citaonica, "resourceId", stoCitaonice, "timeOffId", odmorStola)).toUri();
		MockHttpServletRequestBuilder builder = request(zahtjev.metoda(), adresa)
				.header("Authorization", "Bearer " + token);
		if (zahtjev.tijelo() != null) {
			String almaId = users.findByEmail("alma.izolacija@example.com").orElseThrow().getId().toString();
			builder.contentType(MediaType.APPLICATION_JSON).content(zahtjev.tijelo().replace("{almaId}", almaId));
		}
		return mockMvc.perform(builder).andReturn().getResponse().getStatus();
	}

	private long brojRedova(String tabela) {
		return jdbc.queryForObject("select count(*) from " + tabela + " where organization_id = ?", Long.class,
				citaonica);
	}

	@Test
	void adminDrugeOrganizacijeDobija403NaSvakomEndpointuINistaSeNeMijenja() throws Exception {
		long clanovaPrije = brojRedova("organization_members");
		long pozivnicaPrije = brojRedova("organization_invites");
		long resursaPrije = brojRedova("resources");
		long pravilaPrije = brojPravila();
		long izuzetakaPrije = brojIzuzetaka();

		for (Zahtjev zahtjev : ZAHTJEVI) {
			assertThat(posalji(zahtjev, bojan)).as(zahtjev.ruta()).isEqualTo(403);
		}

		assertThat(brojRedova("organization_members")).isEqualTo(clanovaPrije);
		assertThat(brojRedova("organization_invites")).isEqualTo(pozivnicaPrije);
		assertThat(brojRedova("resources")).isEqualTo(resursaPrije);
		assertThat(jdbc.queryForObject("select slot_minutes from resources where id = ?", Integer.class,
				stoCitaonice)).isEqualTo(120);
		assertThat(jdbc.queryForObject("select active from resources where id = ?", Boolean.class,
				stoCitaonice)).isTrue();
		assertThat(brojPravila()).isEqualTo(pravilaPrije);
		assertThat(brojIzuzetaka()).isEqualTo(izuzetakaPrije);
	}

	private long brojIzuzetaka() {
		return jdbc.queryForObject("select count(*) from time_off where resource_id = ?", Long.class, stoCitaonice);
	}

	// Tabela radnog vremena nema organization_id, pa se broji preko resursa
	private long brojPravila() {
		return jdbc.queryForObject("select count(*) from availability_rules where resource_id = ?", Long.class,
				stoCitaonice);
	}

	// Bez ovoga bi zahtjev s greškom u putanji ili tijelu dao 403 ili 404 iz pogrešnog razloga
	@Test
	void istiZahtjeviSaTokenomAdminaOrganizacijeProlaze() throws Exception {
		for (Zahtjev zahtjev : ZAHTJEVI) {
			assertThat(posalji(zahtjev, alma)).as(zahtjev.ruta()).isBetween(200, 299);
		}
	}

	// Čuvar: novi endpoint pod /api/organizations/{id}/... ili /api/resources/{id} ne može proći mimo
	// ovog testa. Resurs pripada organizaciji i kad je nema u putanji.
	@Test
	void svakaRutaNadPodacimaOrganizacijeJeUOvomTestu() {
		Set<String> uAplikaciji = new TreeSet<>();
		rute.getHandlerMethods().keySet().forEach(info -> info.getPatternValues().stream()
				.filter(putanja -> putanja.matches("/api/organizations/\\{[^}]+}/.+")
						|| putanja.matches("/api/resources/\\{[^}]+}.*"))
				.forEach(putanja -> {
					var metode = info.getMethodsCondition().getMethods();
					// Mapiranje bez metode prima svaku metodu, a skup mu je prazan, pa bi ga forEach preskočio
					if (metode.isEmpty()) {
						uAplikaciji.add("SVE " + putanja);
					}
					metode.forEach(metoda -> uAplikaciji.add(metoda.name() + " " + putanja));
				}));

		Set<String> uTestu = ZAHTJEVI.stream().map(Zahtjev::ruta).collect(Collectors.toCollection(TreeSet::new));

		assertThat(uAplikaciji).isNotEmpty().isEqualTo(uTestu);
	}

}
