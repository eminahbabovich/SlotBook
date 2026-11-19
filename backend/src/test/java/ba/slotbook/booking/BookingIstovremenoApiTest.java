package ba.slotbook.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.jayway.jsonpath.JsonPath;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.availability.AvailabilityInterval;
import ba.slotbook.availability.AvailabilityService;
import ba.slotbook.organization.OrganizationService;
import ba.slotbook.resource.ResourceService;
import ba.slotbook.user.UserService;

// Kriterij "gotovo" Faze 4. Kroz pravi HTTP server, jer MockMvc ne obećava da smije primati
// zahtjeve iz više niti, a ovdje se dokazuje upravo cijeli put: Tomcat, transakcija, baza, 409.
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookingIstovremenoApiTest {

	// Više od 10 konekcija koje daje Hikari, pa dio zahtjeva čeka i na konekciju. To je i stvarna situacija.
	private static final int NITI = 20;
	private static final String LOZINKA = "sebilj-bascarsija-1891";

	@LocalServerPort
	int port;

	@Autowired
	UserService userService;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	ResourceService resourceService;

	@Autowired
	AvailabilityService availabilityService;

	@Autowired
	JdbcTemplate jdbc;

	private final HttpClient http = HttpClient.newHttpClient();

	// Četiri runde, svaka na svoj termin. Deadlock bez zaključavanja resursa zavisi od toga kako se
	// niti poklope, pa ga jedna runda ne uhvati svaki put.
	@Test
	void dvadesetStudenataIstovremenoNaIstiTerminProlaziTacnoJedan() throws Exception {
		UUID profesor = userService.register("edin.istovremeno@example.com", LOZINKA, "Edin").id();
		UUID katedra = organizationService.create(profesor, "Katedra za softversko inženjerstvo",
				"katedra-istovremeno", "Europe/Sarajevo").id();
		UUID konsultacije = resourceService.create(katedra, profesor, "Konsultacije, Algoritmi", null, 30, true, 0).id();
		availabilityService.replace(konsultacije,
				List.of(new AvailabilityInterval(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 0))));

		// Prijave idu prije starta, da niti kreću zajedno, a ne razvučene po BCrypt-u
		List<String> tokeni = new ArrayList<>();
		for (int i = 1; i <= NITI; i++) {
			String email = "student" + i + ".istovremeno@example.com";
			UUID student = userService.register(email, LOZINKA, "Student " + i).id();
			jdbc.update("insert into organization_members values (?, ?, 'CLIENT', now())", katedra, student);
			tokeni.add(prijava(email));
		}

		for (String pocetak : List.of("10:00", "10:30", "11:00", "11:30")) {
			// Nijedan 500: svaki odbijen zahtjev je prepoznat kao zauzet termin
			assertThat(runda(tokeni, konsultacije, "2035-11-19T" + pocetak + ":00+01:00")).as(pocetak)
					.containsOnly(Map.entry(201, 1L), Map.entry(409, (long) NITI - 1));
		}
		assertThat(jdbc.queryForObject("select count(*) from bookings where resource_id = ?", Long.class,
				konsultacije)).isEqualTo(4);
		assertThat(jdbc.queryForObject("select count(*) from booking_events e join bookings b on b.id = e.booking_id "
				+ "where b.resource_id = ?", Long.class, konsultacije)).isEqualTo(4);
	}

	// Broj odgovora po HTTP statusu kad svi studenti u istom trenutku traže isti termin
	private Map<Integer, Long> runda(List<String> tokeni, UUID resurs, String pocetak) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Integer>> odgovori = new ArrayList<>();
		try (ExecutorService niti = Executors.newFixedThreadPool(NITI)) {
			for (String token : tokeni) {
				odgovori.add(niti.submit(() -> {
					start.await();
					return rezervisi(token, resurs, pocetak);
				}));
			}
			start.countDown();
		}
		List<Integer> statusi = new ArrayList<>();
		for (Future<Integer> odgovor : odgovori) {
			statusi.add(odgovor.get());
		}
		return statusi.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
	}

	private String prijava(String email) throws Exception {
		jdbc.update("update users set email_verified_at = now() where email = lower(?)", email);
		HttpResponse<String> odgovor = http.send(post("/api/auth/login", null, """
				{"email": "%s", "password": "%s"}
				""".formatted(email, LOZINKA)), HttpResponse.BodyHandlers.ofString());
		assertThat(odgovor.statusCode()).isEqualTo(200);
		return JsonPath.read(odgovor.body(), "$.accessToken");
	}

	private int rezervisi(String token, UUID resurs, String pocetak) throws Exception {
		return http.send(post("/api/resources/" + resurs + "/bookings", token, """
				{"startTime": "%s"}
				""".formatted(pocetak)), HttpResponse.BodyHandlers.discarding()).statusCode();
	}

	// Status i, kad je 201, id rezervacije, npr. "201 3f2a..." ili samo "409"
	private String rezervisiSKljucem(String token, UUID resurs, String pocetak, String kljuc) throws Exception {
		HttpRequest zahtjev = HttpRequest.newBuilder(post("/api/resources/" + resurs + "/bookings", token, """
				{"startTime": "%s"}
				""".formatted(pocetak)), (ime, vrijednost) -> true)
				.header("Idempotency-Key", kljuc)
				.build();
		HttpResponse<String> odgovor = http.send(zahtjev, HttpResponse.BodyHandlers.ofString());
		return odgovor.statusCode() == 201 ? "201 " + JsonPath.read(odgovor.body(), "$.id")
				: String.valueOf(odgovor.statusCode());
	}

	private HttpRequest post(String putanja, String token, String tijelo) {
		HttpRequest.Builder zahtjev = HttpRequest.newBuilder(URI.create("http://localhost:" + port + putanja))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(tijelo));
		if (token != null) {
			zahtjev.header("Authorization", "Bearer " + token);
		}
		return zahtjev.build();
	}

	// Dupli klik iz deset niti: svi dobiju istu rezervaciju, jer duplikat prvo čeka lock resursa,
	// a tek onda traži raniju rezervaciju s istim ključem
	@Test
	void desetIstovremenihZahtjevaSIstimKljucemDajuJednuRezervaciju() throws Exception {
		UUID profesor = userService.register("lejla.kljuc@example.com", LOZINKA, "Lejla").id();
		UUID katedra = organizationService.create(profesor, "Katedra za matematiku", "katedra-kljuc",
				"Europe/Sarajevo").id();
		UUID konsultacije = resourceService.create(katedra, profesor, "Konsultacije, Analiza", null, 30, true, 0).id();
		availabilityService.replace(konsultacije,
				List.of(new AvailabilityInterval(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 0))));
		UUID student = userService.register("tarik.kljuc@example.com", LOZINKA, "Tarik").id();
		jdbc.update("insert into organization_members values (?, ?, 'CLIENT', now())", katedra, student);
		String token = prijava("tarik.kljuc@example.com");
		String kljuc = UUID.randomUUID().toString();

		CountDownLatch start = new CountDownLatch(1);
		List<Future<String>> odgovori = new ArrayList<>();
		try (ExecutorService niti = Executors.newFixedThreadPool(10)) {
			for (int i = 0; i < 10; i++) {
				odgovori.add(niti.submit(() -> {
					start.await();
					return rezervisiSKljucem(token, konsultacije, "2035-11-19T10:00:00+01:00", kljuc);
				}));
			}
			start.countDown();
		}
		List<String> rezultati = new ArrayList<>();
		for (Future<String> odgovor : odgovori) {
			rezultati.add(odgovor.get());
		}

		assertThat(rezultati).hasSize(10).allMatch(r -> r.startsWith("201 ")).containsOnly(rezultati.getFirst());
		assertThat(jdbc.queryForObject("select count(*) from bookings where resource_id = ?", Long.class,
				konsultacije)).isEqualTo(1);
	}
}
