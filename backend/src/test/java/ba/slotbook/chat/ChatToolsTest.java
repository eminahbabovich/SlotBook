package ba.slotbook.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.jayway.jsonpath.JsonPath;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.availability.AvailabilityInterval;
import ba.slotbook.availability.AvailabilityService;
import ba.slotbook.chat.ChatModel.PozivAlata;
import ba.slotbook.chat.ChatTools.Kontekst;
import ba.slotbook.chat.ChatTools.RezultatAlata;
import ba.slotbook.organization.OrganizationService;
import ba.slotbook.resource.ResourceService;
import ba.slotbook.user.UserService;

// Alati kroz prave servise i bazu. Lejla je klijent čitaonice i član teretane, pa test vidi i da alat
// ne prelazi u drugu organizaciju u kojoj ona inače ima pravo. Datumi su u martu 2035: 25. je pomjeranje
// sata u Sarajevu (u 02:00 sat ide na 03:00), pa je 08:00 dan prije 07:00 UTC, a od tog dana 06:00 UTC.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ChatToolsTest {

	private static final ZoneId SARAJEVO = ZoneId.of("Europe/Sarajevo");

	@Autowired
	ChatTools alati;

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

	private final String sufiks = UUID.randomUUID().toString().substring(0, 6);
	private UUID amila;
	private UUID lejla;
	private UUID citaonica;
	private UUID teretana;
	private UUID sto4;
	private UUID sala3;
	private UUID stariSto;
	private Kontekst lejlaUCitaonici;

	@BeforeEach
	void citaonicaITeretana() {
		amila = korisnik("amila", "Amila Begić");
		lejla = korisnik("lejla", "Lejla Šehić");
		UUID bojan = korisnik("bojan", "Bojan Marić");
		citaonica = organizationService.create(amila, "Čitaonica FIT", "citaonica-alati-" + sufiks, SARAJEVO.getId()).id();
		teretana = organizationService.create(bojan, "Teretana Grbavica", "teretana-alati-" + sufiks, SARAJEVO.getId()).id();
		clan(citaonica, lejla);
		clan(teretana, lejla);

		sto4 = otvorenSvakiDan(resourceService.create(citaonica, amila, "Sto 4, tihi dio", "Uz prozor, bez utičnice",
				60, true, 0, 1, 0).id());
		otvorenSvakiDan(resourceService.create(citaonica, amila, "Sala za grupni rad", null, 60, false, 0, 1, 500).id());
		stariSto = otvorenSvakiDan(resourceService.create(citaonica, amila, "Stari sto", null, 60, true, 0, 1, 0).id());
		resourceService.deactivate(citaonica, amila, stariSto);
		sala3 = otvorenSvakiDan(resourceService.create(teretana, bojan, "Sala 3", null, 60, true, 0, 1, 0).id());

		lejlaUCitaonici = new Kontekst(citaonica, lejla, token(lejla));
	}

	@Test
	void definicijeImajuCetiriAlataSaJsonShemom() {
		assertThat(alati.definicije()).extracting(alat -> alat.function().name())
				.containsExactly("list_resources", "find_free_slots", "check_availability", "get_my_bookings");
		assertThat(alati.definicije()).allSatisfy(alat -> assertThat(alat.function().parameters())
				.containsEntry("type", "object"));
	}

	@Test
	void listResourcesKlijentuDajeSamoAktivneSaCijenomIPotvrdom() {
		String json = uspjeh(pozovi("list_resources", "{}"));

		assertThat(JsonPath.<List<String>>read(json, "$.resources[*].name"))
				.containsExactly("Sala za grupni rad", "Sto 4, tihi dio");
		assertThat(JsonPath.<Double>read(json, "$.resources[0].price_km")).isEqualTo(5.0);
		assertThat(JsonPath.<Boolean>read(json, "$.resources[0].needs_confirmation")).isTrue();
		assertThat(JsonPath.<String>read(json, "$.resources[1].description")).isEqualTo("Uz prozor, bez utičnice");
	}

	// Model vidi samo lokalno vrijeme. Da alat formatira u UTC-u, 24. bi počinjao u 07:00, a 25. u 06:00.
	@Test
	void findFreeSlotsDajeLokalnoVrijemeIPrekoPomjeranjaSata() {
		String json = uspjeh(pozovi("find_free_slots", "{\"date\": \"2035-03-24\", \"days\": 2, \"from_hour\": 8, \"to_hour\": 9}"));

		assertThat(JsonPath.<String>read(json, "$.timezone")).isEqualTo("Europe/Sarajevo");
		assertThat(JsonPath.<List<String>>read(json, "$.slots[*].date"))
				.containsExactly("2035-03-24", "2035-03-24", "2035-03-25", "2035-03-25");
		assertThat(JsonPath.<List<String>>read(json, "$.slots[*].start")).containsOnly("08:00");
		assertThat(JsonPath.<List<String>>read(json, "$.slots[*].end")).containsOnly("09:00");
		assertThat(JsonPath.<List<String>>read(json, "$.slots[*].resource"))
				.doesNotContain("Stari sto", "Sala 3");
		assertThat(json).doesNotContain("T07:00", "T06:00", "Z\"");
	}

	@Test
	void findFreeSlotsZaTriDanaJeGreskaSaPorukomZaModel() {
		RezultatAlata rezultat = pozovi("find_free_slots", "{\"date\": \"2035-03-24\", \"days\": 3}");

		assertThat(rezultat.greska()).isTrue();
		assertThat(rezultat.sadrzaj()).contains("jedan ili dva dana");
	}

	@Test
	void checkAvailabilityPokazujeSlobodneTermineIMojuRezervaciju() {
		rezervacija(sto4, "2035-03-26", 10, "CONFIRMED");

		String json = uspjeh(pozovi("check_availability",
				"{\"resource_id\": \"" + sto4 + "\", \"date\": \"2035-03-26\"}"));

		assertThat(JsonPath.<String>read(json, "$.resource")).isEqualTo("Sto 4, tihi dio");
		assertThat(JsonPath.<List<String>>read(json, "$.free[*].start"))
				.containsExactly("08:00", "09:00", "11:00", "12:00", "13:00", "14:00", "15:00");
		assertThat(JsonPath.<List<String>>read(json, "$.my_bookings[*].start")).containsExactly("10:00");
		assertThat(JsonPath.<List<String>>read(json, "$.my_bookings[*].status")).containsExactly("CONFIRMED");
	}

	// Lejla je član teretane, pa bi joj sam ResourceAccess.canView pustio Salu 3. Sesija je u čitaonici.
	@Test
	void resursDrugeOrganizacijeJeGreskaIakoJeKorisnikTamoClan() {
		RezultatAlata rezultat = pozovi("check_availability",
				"{\"resource_id\": \"" + sala3 + "\", \"date\": \"2035-03-26\"}");

		assertThat(rezultat.greska()).isTrue();
		assertThat(rezultat.sadrzaj()).contains("nije pronađen").doesNotContain("Sala 3", "08:00");
	}

	@Test
	void deaktiviranResursKlijentNeVidiNiKrozAlat() {
		RezultatAlata rezultat = pozovi("check_availability",
				"{\"resource_id\": \"" + stariSto + "\", \"date\": \"2035-03-26\"}");

		assertThat(rezultat.greska()).isTrue();
		assertThat(rezultat.sadrzaj()).doesNotContain("Stari sto");
	}

	@Test
	void getMyBookingsDajeSamoBuduceKojeDrzeTerminUOvojOrganizaciji() {
		UUID buduca = rezervacija(sto4, "2035-03-26", 10, "CONFIRMED");
		rezervacija(sto4, "2020-03-26", 10, "CONFIRMED");
		rezervacija(sto4, "2035-03-27", 10, "CANCELLED");
		rezervacija(sala3, "2035-03-26", 11, "CONFIRMED");

		String json = uspjeh(pozovi("get_my_bookings", "{}"));

		assertThat(JsonPath.<List<String>>read(json, "$.bookings[*].id")).containsExactly(buduca.toString());
		assertThat(JsonPath.<String>read(json, "$.bookings[0].resource")).isEqualTo("Sto 4, tihi dio");
		assertThat(JsonPath.<String>read(json, "$.bookings[0].date")).isEqualTo("2035-03-26");
		assertThat(JsonPath.<String>read(json, "$.bookings[0].start")).isEqualTo("10:00");
		assertThat(JsonPath.<String>read(json, "$.bookings[0].end")).isEqualTo("11:00");
	}

	// Model zna napisati bilo šta. Alat tada vraća grešku kao rezultat, a ne baca izuzetak koji bi
	// oborio cijeli zahtjev.
	@Test
	void losiArgumentiINepoznatAlatSuRezultatSGreskom() {
		assertThat(pozovi("find_free_slots", "{datum: sutra")).satisfies(this::greskaSTekstom);
		assertThat(pozovi("find_free_slots", "{\"date\": \"sutra\"}")).satisfies(this::greskaSTekstom);
		assertThat(pozovi("check_availability", "{\"resource_id\": \"sto-4\", \"date\": \"2035-03-26\"}"))
				.satisfies(this::greskaSTekstom);
		assertThat(pozovi("create_everything", "{}")).satisfies(this::greskaSTekstom);
	}

	private void greskaSTekstom(RezultatAlata rezultat) {
		assertThat(rezultat.greska()).isTrue();
		assertThat(rezultat.sadrzaj()).isNotBlank();
	}

	private RezultatAlata pozovi(String alat, String argumenti) {
		return alati.izvrsi(new PozivAlata("call_1", "function", new PozivAlata.Funkcija(alat, argumenti)),
				lejlaUCitaonici);
	}

	private static String uspjeh(RezultatAlata rezultat) {
		assertThat(rezultat.greska()).as(rezultat.sadrzaj()).isFalse();
		return rezultat.sadrzaj();
	}

	private UUID rezervacija(UUID resurs, String datum, int sat, String status) {
		UUID id = UUID.randomUUID();
		var pocetak = LocalDate.parse(datum).atTime(sat, 0).atZone(SARAJEVO).toInstant();
		jdbc.update("insert into bookings (id, resource_id, client_id, start_time, end_time, status, created_at) "
				+ "values (?, ?, ?, ?, ?, ?, now())", id, resurs, lejla, Timestamp.from(pocetak),
				Timestamp.from(pocetak.plusSeconds(3600)), status);
		return id;
	}

	private UUID otvorenSvakiDan(UUID resurs) {
		availabilityService.replace(resurs, Arrays.stream(DayOfWeek.values())
				.map(d -> new AvailabilityInterval(d, LocalTime.of(8, 0), LocalTime.of(16, 0))).toList());
		return resurs;
	}

	private void clan(UUID organizacija, UUID korisnik) {
		jdbc.update("insert into organization_members values (?, ?, 'CLIENT', now())", organizacija, korisnik);
	}

	private UUID korisnik(String ime, String imeIPrezime) {
		return userService.register(ime + ".alati." + sufiks + "@example.com", "alati-za-citanje-4", imeIPrezime).id();
	}

	private static JwtAuthenticationToken token(UUID korisnik) {
		return new JwtAuthenticationToken(Jwt.withTokenValue("token").header("alg", "none")
				.subject(korisnik.toString()).build());
	}

}
