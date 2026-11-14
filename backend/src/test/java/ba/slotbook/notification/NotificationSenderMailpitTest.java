package ba.slotbook.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.jayway.jsonpath.JsonPath;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.availability.AvailabilityInterval;
import ba.slotbook.availability.AvailabilityService;
import ba.slotbook.booking.BookingService;
import ba.slotbook.organization.OrganizationService;
import ba.slotbook.resource.ResourceService;
import ba.slotbook.user.User;
import ba.slotbook.user.UserRepository;

// Cijeli put: rezervacija upiše obavijest, pošiljalac je pošalje pravim SMTP-om, a Mailpit je primi.
// Isti Mailpit kao u docker-compose, pa se ovdje vidi i da č, ć, š, đ, ž stignu kako treba.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Testcontainers
class NotificationSenderMailpitTest {

	// Ponedjeljak, 19.11.2035. u 09:00 po sarajevskom vremenu
	private static final Instant PONEDJELJAK_09 = Instant.parse("2035-11-19T08:00:00Z");

	@Container
	static final GenericContainer<?> MAILPIT = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.33"))
			.withExposedPorts(1025, 8025);

	@DynamicPropertySource
	static void smtp(DynamicPropertyRegistry registry) {
		registry.add("spring.mail.host", MAILPIT::getHost);
		registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
	}

	@Autowired
	NotificationSender sender;

	@Autowired
	BookingService bookingService;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	ResourceService resourceService;

	@Autowired
	AvailabilityService availabilityService;

	@Autowired
	UserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PlatformTransactionManager transakcije;

	private String email;
	private UUID studentica;
	private UUID sto;

	@BeforeEach
	void stoUCitaoniciNaAutomatskojPotvrdi() {
		String oznaka = UUID.randomUUID().toString().substring(0, 8);
		UUID profesorica = korisnik("amila." + oznaka + "@example.com", "Amila");
		email = "dzenana." + oznaka + "@example.com";
		studentica = korisnik(email, "Dženana");
		UUID citaonica = organizationService.create(profesorica, "Čitaonica FIT", "citaonica-" + oznaka,
				"Europe/Sarajevo").id();
		sto = resourceService.create(citaonica, profesorica, "Sto br. 3 u čitaonici", null, 60, true, 0).id();
		availabilityService.replace(sto,
				List.of(new AvailabilityInterval(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))));
	}

	private UUID korisnik(String adresa, String ime) {
		return users.save(new User(adresa, "hash", ime, Instant.parse("2035-11-01T10:00:00Z"))).getId();
	}

	private String mailpit(String putanja) throws Exception {
		URI adresa = URI.create("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025) + putanja);
		try (HttpClient klijent = HttpClient.newHttpClient()) {
			return klijent.send(HttpRequest.newBuilder(adresa).build(), HttpResponse.BodyHandlers.ofString()).body();
		}
	}

	private UUID obavijestStudentice() {
		return jdbc.queryForObject("select id from notifications where user_id = ?", UUID.class, studentica);
	}

	@Test
	void potvrdaStizeMailomSNasimSlovimaIVremenomUZoniOrganizacije() throws Exception {
		bookingService.create(sto, studentica, PONEDJELJAK_09, null);

		assertThat(sender.sendNext()).isTrue();

		List<String> poruke = JsonPath.read(mailpit("/api/v1/messages"),
				"$.messages[?(@.To[0].Address == '%s')].ID".formatted(email));
		assertThat(poruke).hasSize(1);
		String poruka = mailpit("/api/v1/message/" + poruke.get(0));
		assertThat((String) JsonPath.read(poruka, "$.Subject")).isEqualTo("Rezervacija potvrđena: Sto br. 3 u čitaonici");
		assertThat((String) JsonPath.read(poruka, "$.Text"))
				.contains("Termin ponedjeljak, 19. novembar 2035. u 09:00 (Sto br. 3 u čitaonici) je tvoj.");
		assertThat(jdbc.queryForMap("select status, attempts, sent_at, body from notifications where id = ?",
				obavijestStudentice())).containsEntry("status", "SENT").containsEntry("attempts", 1)
				.containsEntry("body", null)
				.hasEntrySatisfying("sent_at", vrijeme -> assertThat(vrijeme).isNotNull());
	}

	// Prva instanca drži red dok šalje. Druga ga ne smije čekati ni poslati: preskoči ga.
	// lock_timeout je tu da test padne za 2 sekunde, a ne da visi, ako SKIP LOCKED nestane.
	@Test
	void obavijestKojuSaljeDrugaInstancaSePreskace() {
		bookingService.create(sto, studentica, PONEDJELJAK_09, null);
		UUID obavijest = obavijestStudentice();
		TransactionTemplate prva = new TransactionTemplate(transakcije);
		TransactionTemplate druga = new TransactionTemplate(transakcije);
		druga.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

		Boolean drugaJePoslala = prva.execute(s -> {
			jdbc.queryForObject("select id from notifications where id = ? for update", UUID.class, obavijest);
			return druga.execute(s2 -> {
				jdbc.execute("set local lock_timeout = '2s'");
				return sender.sendNext();
			});
		});

		assertThat(drugaJePoslala).isFalse();
		assertThat(sender.sendNext()).isTrue();
	}

}
