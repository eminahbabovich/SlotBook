package ba.slotbook.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.UriComponentsBuilder;

import com.jayway.jsonpath.JsonPath;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.chat.LazniChatModel;
import ba.slotbook.user.UserRepository;

// Kriterij "gotovo" Faze 2: korisnik iz organizacije A ne može dotaknuti podatke organizacije B
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OrganizationIsolationApiTest {

	private static final String LOZINKA = "kozija-cuprija-1580";

	// Svaki endpoint nad podacima jedne organizacije. Tijelo mora biti ispravno: @Valid se izvrši
	// prije @PreAuthorize, pa bi neispravno tijelo dalo 400 i sakrilo da autorizacija fali.
	// Isto važi za obavezne parametre iza ?, pa i oni moraju biti ispravni.
	private record Zahtjev(HttpMethod metoda, String putanja, String tijelo) {

		// Umjesto JSON tijela ide multipart s ispravnom slikom
		static final String SLIKA = "<multipart slika>";

		// Ruta bez parametara, da se poredi s obrascem iz aplikacije. Ime varijable rezervacije
		// u testu kaže u kojem je stanju, a u aplikaciji je {bookingId}.
		String ruta() {
			return metoda.name() + " " + putanja.split("\\?")[0].replaceFirst("/api/bookings/\\{[^}]+}",
					"/api/bookings/{bookingId}").replaceFirst("/members/\\{[^}]+}", "/members/{userId}")
					.replaceFirst("/api/chat/proposals/\\{[^}]+}", "/api/chat/proposals/{proposalId}");
		}

	}

	private static final List<Zahtjev> ZAHTJEVI = List.of(
			new Zahtjev(HttpMethod.GET, "/api/organizations/{id}/members", null),
			new Zahtjev(HttpMethod.GET, "/api/organizations/{id}/audit-log", null),
			new Zahtjev(HttpMethod.GET, "/api/organizations/{id}/invites", null),
			new Zahtjev(HttpMethod.DELETE, "/api/organizations/{id}/invites/{inviteId}", null),
			new Zahtjev(HttpMethod.GET, "/api/organizations/{id}/invite-links", null),
			new Zahtjev(HttpMethod.POST, "/api/organizations/{id}/invite-links", """
					{"maxUses": 5, "days": 7}
					"""),
			new Zahtjev(HttpMethod.DELETE, "/api/organizations/{id}/invite-links/{linkId}", null),
			new Zahtjev(HttpMethod.GET, "/api/organizations/{id}/screens", null),
			new Zahtjev(HttpMethod.POST, "/api/organizations/{id}/screens", "{}"),
			new Zahtjev(HttpMethod.DELETE, "/api/organizations/{id}/screens/{screenId}", null),
			new Zahtjev(HttpMethod.POST, "/api/organizations/{id}/chat/sessions", null),
			// Razgovor je Almin, pa ga ni klijent ni provider iste organizacije ne čitaju
			new Zahtjev(HttpMethod.GET, "/api/chat/sessions/{sessionId}/messages", null),
			new Zahtjev(HttpMethod.POST, "/api/chat/sessions/{sessionId}/messages", """
					{"text": "Pokaži mi rezervacije"}
					"""),
			// Almini prijedlozi iz chata: potvrda otkazuje njenu rezervaciju, a odbijanje gasi prijedlog termina
			new Zahtjev(HttpMethod.POST, "/api/chat/proposals/{prijedlogZaPotvrdu}/confirm", null),
			new Zahtjev(HttpMethod.POST, "/api/chat/proposals/{prijedlogZaOdbijanje}/dismiss", null),
			// Lejli se daje uloga koju već ima: admin dobije 200, a ništa se ne promijeni
			new Zahtjev(HttpMethod.PUT, "/api/organizations/{id}/members/{lejlaId}/role", """
					{"role": "CLIENT"}
					"""),
			// Prazno tijelo: pravila nema ni prije ni poslije, pa admin dobije 200, a ništa se ne promijeni
			new Zahtjev(HttpMethod.PUT, "/api/organizations/{id}/no-show-rules", "{}"),
			// Kenan je klijent bez rezervacija, samo za ovo. Prije svakog testa se vraća u čitaonicu.
			new Zahtjev(HttpMethod.DELETE, "/api/organizations/{id}/members/{kenanId}", null),
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
			// Prije DELETE-a, jer se deaktiviran resurs ne može rezervisati. Trajanje je tada 90 minuta, pa 08:00 odgovara.
			new Zahtjev(HttpMethod.POST, "/api/resources/{resourceId}/bookings", """
					{"startTime": "2035-08-06T08:00:00+02:00"}
					"""),
			// Druge dvije sedmice, da se ne sudari s rezervacijom iznad
			new Zahtjev(HttpMethod.POST, "/api/resources/{resourceId}/bookings/series", """
					{"startTime": "2035-08-13T08:00:00+02:00", "weeks": 2}
					"""),
			// 14:00 drži Selmina rezervacija. Termin postoji i s 90 i sa 120 minuta trajanja.
			new Zahtjev(HttpMethod.POST, "/api/resources/{resourceId}/waitlist", """
					{"startTime": "2036-01-07T14:00:00+01:00"}
					"""),
			new Zahtjev(HttpMethod.DELETE, "/api/waitlist/{waitlistId}", null),
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
			new Zahtjev(HttpMethod.DELETE, "/api/resources/{resourceId}/time-off/{timeOffId}", null),
			new Zahtjev(HttpMethod.GET, "/api/resources/{resourceId}/slots?from=2035-08-06&to=2035-08-13", null),
			new Zahtjev(HttpMethod.GET, "/api/resources/{resourceId}/slots/stream", null),
			new Zahtjev(HttpMethod.GET, "/api/resources/{resourceId}/bookings?from=2035-08-06&to=2035-08-13", null),
			new Zahtjev(HttpMethod.GET, "/api/organizations/{id}/stats?from=2035-08-01&to=2035-09-01", null),
			new Zahtjev(HttpMethod.GET, "/api/organizations/{id}/free-slots?date=2035-08-06&days=2", null),
			// POST prije GET-a, da admin u drugom testu ima šta pročitati
			new Zahtjev(HttpMethod.POST, "/api/resources/{resourceId}/image", Zahtjev.SLIKA),
			new Zahtjev(HttpMethod.GET, "/api/resources/{resourceId}/image", null),
			// Svaki prelaz na svojoj rezervaciji, u stanju iz kojeg je prelaz dozvoljen
			new Zahtjev(HttpMethod.GET, "/api/bookings/{zaPotvrdu}/events", null),
			new Zahtjev(HttpMethod.POST, "/api/bookings/{zaPotvrdu}/confirm", null),
			new Zahtjev(HttpMethod.POST, "/api/bookings/{zaOdbijanje}/reject", null),
			new Zahtjev(HttpMethod.POST, "/api/bookings/{zaOtkazivanje}/cancel", null),
			new Zahtjev(HttpMethod.POST, "/api/bookings/{zaDolazak}/check-in", null),
			new Zahtjev(HttpMethod.POST, "/api/bookings/{zaNedolazak}/no-show", null),
			// Link za QR traži samo klijent (Alma), a kod iz njega skenira onaj ko vodi resurs
			new Zahtjev(HttpMethod.GET, "/api/bookings/{zaQrDolazak}/check-in-link", null),
			new Zahtjev(HttpMethod.POST, "/api/bookings/{zaQrDolazak}/check-in/qr", """
					{"token": "{qrKod}"}
					"""),
			// Plaća samo klijent (Alma), pa ni admin tuđe organizacije ni drugi član ne otvaraju uplatu
			new Zahtjev(HttpMethod.POST, "/api/bookings/{zaUplatu}/pay", null));

	// Šta smije klijent čitaonice: vidi aktivan resurs, radno vrijeme, termine i sliku, i rezerviše.
	// Rezervacije u testu su Almine, pa njihovu historiju i otkazivanje ne smije. Sve ostalo je 403.
	private static final Set<String> KLIJENT_SMIJE = Set.of(
			"GET /api/organizations/{id}/resources",
			"POST /api/organizations/{id}/chat/sessions",
			"GET /api/organizations/{id}/free-slots",
			"GET /api/resources/{resourceId}",
			"GET /api/resources/{resourceId}/availability",
			"GET /api/resources/{resourceId}/slots",
			"GET /api/resources/{resourceId}/slots/stream",
			"GET /api/resources/{resourceId}/image",
			"POST /api/resources/{resourceId}/bookings",
			"POST /api/resources/{resourceId}/bookings/series",
			"POST /api/resources/{resourceId}/waitlist");

	// Provider drugog stola uz to pravi svoj resurs i vidi statistiku (samo svojih resursa, to dokazuje
	// StatsApiTest). Tuđi sto ne uređuje i tuđe rezervacije ne vodi, iako je u istoj organizaciji.
	private static final Set<String> PROVIDER_DRUGOG_SMIJE = Set.of(
			"GET /api/organizations/{id}/resources",
			"POST /api/organizations/{id}/chat/sessions",
			"GET /api/organizations/{id}/free-slots",
			"GET /api/resources/{resourceId}",
			"GET /api/resources/{resourceId}/availability",
			"GET /api/resources/{resourceId}/slots",
			"GET /api/resources/{resourceId}/slots/stream",
			"GET /api/resources/{resourceId}/image",
			"POST /api/resources/{resourceId}/bookings",
			"POST /api/resources/{resourceId}/bookings/series",
			"POST /api/resources/{resourceId}/waitlist",
			"POST /api/organizations/{id}/resources",
			"GET /api/organizations/{id}/stats");

	// Rute koje nisu podaci jedne organizacije, i zašto. Nova ruta mora ući u ZAHTJEVI ili ovdje.
	private static final Map<String, String> NISU_PODACI_ORGANIZACIJE = Map.ofEntries(
			Map.entry("GET /api/status", "javno, bez podataka"),
			Map.entry("POST /api/auth/register", "javno"),
			Map.entry("POST /api/auth/login", "javno"),
			Map.entry("POST /api/auth/refresh", "javno, nosi refresh kolačić"),
			Map.entry("POST /api/auth/logout", "javno, nosi refresh kolačić"),
			Map.entry("POST /api/auth/verify-email", "javno, pravo daje token iz maila"),
			Map.entry("POST /api/auth/password-reset", "javno, isti odgovor za svaki email"),
			Map.entry("POST /api/auth/password-reset/confirm", "javno, pravo daje token iz maila"),
			Map.entry("GET /api/me", "samo korisnik iz tokena"),
			Map.entry("DELETE /api/me", "samo korisnik iz tokena, AccountDeletionApiTest"),
			Map.entry("POST /api/auth/email-verification", "javno, uvijek 204, mail ide samo vlasniku emaila"),
			Map.entry("GET /api/me/organizations", "samo korisnik iz tokena"),
			Map.entry("GET /api/me/export", "samo korisnik iz tokena, MyDataExportApiTest provjerava da tuđeg nema"),
			Map.entry("GET /api/bookings/mine", "samo korisnik iz tokena"),
			Map.entry("GET /api/me/blocks", "samo korisnik iz tokena, BookingRulesApiTest"),
			Map.entry("GET /api/waitlist/mine", "samo korisnik iz tokena, WaitlistApiTest provjerava da tuđeg nema"),
			Map.entry("GET /api/me/calendar-feed", "samo korisnik iz tokena"),
			Map.entry("POST /api/me/calendar-feed", "samo korisnik iz tokena"),
			Map.entry("DELETE /api/me/calendar-feed", "samo korisnik iz tokena"),
			Map.entry("GET /api/me/calendar.ics", "javno, pravo daje token iz linka, CalendarFeedApiTest provjerava da tuđeg nema"),
			Map.entry("POST /api/organizations", "nova organizacija, kreator postaje admin"),
			Map.entry("GET /api/organizations/{slug}", "javni profil: naziv, adresa i zona"),
			Map.entry("POST /api/invites/accept", "pravo daju token i email iz pozivnice"),
			Map.entry("GET /api/invite-links/{token}", "javno, samo ime organizacije, pravo daje token iz linka"),
			Map.entry("POST /api/invite-links/join", "pravo daje token, ulazi se samo kao klijent, InviteLinkApiTest"),
			Map.entry("GET /api/screens/{token}", "javno, pravo daje token ekrana, bez podataka o ljudima, DoorScreenApiTest"),
			Map.entry("GET /api/screens/{token}/stream", "javno, pravo daje token ekrana, šalje samo ID resursa, DoorScreenApiTest"),
			Map.entry("GET /api/push/public-key", "javni VAPID ključ servera, isti za sve"),
			Map.entry("PUT /api/me/push-subscriptions", "samo korisnik iz tokena, PushApiTest"),
			Map.entry("DELETE /api/me/push-subscriptions", "samo browser korisnika iz tokena, PushApiTest provjerava tuđi"),
			Map.entry("POST /api/me/push-subscriptions/test", "samo browser korisnika iz tokena, PushApiTest provjerava tuđi"),
			Map.entry("POST /api/stripe/webhook", "javno, pravo daje Stripe potpis, StripeWebhookApiTest"));

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13 };

	// Vlasnik razgovora dobije odgovor, a tuđi tokeni ne smiju ni doći do modela
	@Autowired
	LazniChatModel model;

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
	private String lejla;
	private String damir;
	private UUID citaonica;
	private UUID teretana;
	private UUID stoCitaonice;
	private UUID odmorStola;
	private Map<String, UUID> rezervacije;
	private UUID alminUpis;
	private UUID otvorenaPozivnica;
	private UUID pozivniLink;
	private UUID ekran;
	private UUID razgovor;
	private Map<String, UUID> prijedlozi;

	// Bojan je admin, ali svoje teretane. Provjera "admin bilo gdje" bi ga pustila, običan član ne bi to otkrio.
	// Lejla je klijent čitaonice, a Damir u čitaonici vodi drugi sto.
	@BeforeEach
	void almaJeAdminCitaoniceBojanAdminTeretane() throws Exception {
		alma = token("alma.izolacija@example.com", "Alma");
		bojan = token("bojan.izolacija@example.com", "Bojan");
		lejla = token("lejla.izolacija@example.com", "Lejla Čolić");
		damir = token("damir.izolacija@example.com", "Damir Đonko");
		citaonica = organizacija(alma, "citaonica-izolacija");
		teretana = organizacija(bojan, "teretana-izolacija");
		clan(citaonica, "lejla.izolacija@example.com", "CLIENT");
		clan(citaonica, "damir.izolacija@example.com", "PROVIDER");
		drugiSto();
		stoCitaonice = stoCitaonice();
		odmorStola = odmorStola();
		ponedjeljkomOtvoreno();
		rezervacije = rezervacijeZaPrelaze();
		alminUpis = selmaDrziTerminAAlmaCeka();
		token("kenan.izolacija@example.com", "Kenan Hodžić");
		clan(citaonica, "kenan.izolacija@example.com", "CLIENT");
		otvorenaPozivnica = otvorenaPozivnica();
		pozivniLink = pozivniLink();
		ekran = ekranNaVratima();
		razgovor = alminRazgovor();
		model.ocisti();
		model.kaze("U redu.");
		prijedlozi = alminiPrijedlozi();
	}

	// Almino povlačenje ih ukloni, pa se prave nove prije svakog testa
	private UUID otvorenaPozivnica() {
		jdbc.update("delete from organization_invites where organization_id = ? and email = 'nova.izolacija@example.com'",
				citaonica);
		UUID id = UUID.randomUUID();
		jdbc.update("insert into organization_invites (id, organization_id, email, role, token_hash, created_at, "
				+ "expires_at) values (?, ?, 'nova.izolacija@example.com', 'CLIENT', ?, now(), now() + interval '7 days')",
				id, citaonica, UUID.randomUUID().toString().replace("-", ""));
		return id;
	}

	private UUID pozivniLink() {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into invite_links (id, organization_id, token_hash, max_uses, expires_at, created_at) "
				+ "values (?, ?, ?, 5, now() + interval '7 days', now())", id, citaonica,
				UUID.randomUUID().toString().replace("-", ""));
		return id;
	}

	private UUID ekranNaVratima() {
		UUID id = UUID.randomUUID();
		jdbc.update("update door_screens set revoked_at = now() where organization_id = ? and revoked_at is null",
				citaonica);
		jdbc.update("insert into door_screens (id, organization_id, token_hash, created_at) values (?, ?, ?, now())",
				id, citaonica, UUID.randomUUID().toString().replace("-", ""));
		return id;
	}

	private UUID alminRazgovor() {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into chat_sessions (id, organization_id, user_id, created_at, last_message_at) "
				+ "values (?, ?, ?, now(), now())", id, citaonica,
				users.findByEmail("alma.izolacija@example.com").orElseThrow().getId());
		return id;
	}

	// Rezervacije sa stola se brišu prije svakog testa, a s njima (ON DELETE CASCADE) i prijedlozi
	private Map<String, UUID> alminiPrijedlozi() {
		UUID zaOtkazati = rezervacija(users.findByEmail("alma.izolacija@example.com").orElseThrow().getId(),
				"2036-01-07 13:00+01", "CONFIRMED");
		UUID zaPotvrdu = UUID.randomUUID();
		jdbc.update("insert into chat_proposals (id, session_id, action, booking_id, idempotency_key, created_at, "
				+ "expires_at) values (?, ?, 'CANCEL', ?, ?, now(), now() + interval '10 minutes')", zaPotvrdu, razgovor,
				zaOtkazati, "chat-" + zaPotvrdu);
		UUID zaOdbijanje = UUID.randomUUID();
		jdbc.update("insert into chat_proposals (id, session_id, action, resource_id, start_time, idempotency_key, "
				+ "created_at, expires_at) values (?, ?, 'BOOK', ?, '2036-01-14 08:00+01', ?, now(), "
				+ "now() + interval '10 minutes')", zaOdbijanje, razgovor, stoCitaonice, "chat-" + zaOdbijanje);
		return Map.of("prijedlogZaPotvrdu", zaPotvrdu, "prijedlogZaOdbijanje", zaOdbijanje);
	}

	// Ukupno i povučenih: tuđi POST bi dodao link, a tuđi DELETE bi ga povukao
	private String brojPovucenihLinkova() {
		// Isto za ekrane na vratima
		return jdbc.queryForObject("select (select count(*) || '/' || count(revoked_at) from invite_links "
				+ "where organization_id = ?) || ' ' || (select count(*) || '/' || count(revoked_at) from door_screens "
				+ "where organization_id = ?)", String.class, citaonica, citaonica);
	}

	// Almin prelaz ih promijeni, pa se prije svakog testa prave nove. Klijent je Alma, admin čitaonice.
	// Nedolazak traži termin koji je počeo, zato je on prije dva sata. Ne ranije: BookingJob potvrđene
	// zatvara dan poslije kraja, pa bi stara rezervacija usred testa postala COMPLETED.
	private Map<String, UUID> rezervacijeZaPrelaze() {
		jdbc.update("delete from waitlist_entries where resource_id = ?", stoCitaonice);
		jdbc.update("delete from booking_events where booking_id in (select id from bookings where resource_id = ?)",
				stoCitaonice);
		jdbc.update("delete from notifications where booking_id in (select id from bookings where resource_id = ?)",
				stoCitaonice);
		jdbc.update("delete from payments where booking_id in (select id from bookings where resource_id = ?)",
				stoCitaonice);
		jdbc.update("delete from bookings where resource_id = ?", stoCitaonice);
		UUID almaId = users.findByEmail("alma.izolacija@example.com").orElseThrow().getId();
		return Map.of(
				"zaPotvrdu", rezervacija(almaId, "2036-01-07 08:00+01", "PENDING"),
				"zaOdbijanje", rezervacija(almaId, "2036-01-07 09:00+01", "PENDING"),
				"zaOtkazivanje", rezervacija(almaId, "2036-01-07 10:00+01", "CONFIRMED"),
				"zaDolazak", rezervacija(almaId, "2036-01-07 11:00+01", "CONFIRMED"),
				"zaQrDolazak", rezervacija(almaId, "2036-01-07 12:00+01", "CONFIRMED"),
				"zaNedolazak", rezervacija(almaId, OffsetDateTime.now().minusHours(2).toString(), "CONFIRMED"),
				"zaUplatu", placena(rezervacija(almaId, "2036-01-07 16:00+01", "PENDING")));
	}

	// Cijena se upisuje na rezervaciju, a ne na sto, da ostale rezervacije ostanu besplatne
	private UUID placena(UUID rezervacija) {
		jdbc.update("update bookings set price_cents = 1500 where id = ?", rezervacija);
		return rezervacija;
	}

	private long brojUplata() {
		return jdbc.queryForObject("select count(*) from payments p join bookings b on b.id = p.booking_id "
				+ "where b.resource_id = ?", Long.class, stoCitaonice);
	}

	// Alma se ispisuje sa svog upisa, a ostali ga ne smiju dirati. Selma nije ni u jednoj organizaciji,
	// samo drži termin, jer se na svoj termin niko ne upisuje na listu.
	private UUID selmaDrziTerminAAlmaCeka() throws Exception {
		token("selma.izolacija@example.com", "Selma Šabić");
		rezervacija(users.findByEmail("selma.izolacija@example.com").orElseThrow().getId(), "2036-01-07 14:00+01",
				"CONFIRMED");
		UUID id = UUID.randomUUID();
		jdbc.update("insert into waitlist_entries (id, resource_id, client_id, start_time, end_time, status, "
				+ "created_at) values (?, ?, ?, '2036-01-07 14:00+01', '2036-01-07 16:00+01', 'WAITING', now())", id,
				stoCitaonice, users.findByEmail("alma.izolacija@example.com").orElseThrow().getId());
		return id;
	}

	private String statusAlminogUpisa() {
		return jdbc.queryForObject("select status from waitlist_entries where id = ?", String.class, alminUpis);
	}

	private UUID rezervacija(UUID klijent, String pocetak, String status) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into bookings (id, resource_id, client_id, start_time, end_time, status, created_at, "
				+ "expires_at) values (?, ?, ?, ?::timestamptz, ?::timestamptz + interval '1 hour', ?, now(), "
				+ "?::timestamptz)", id, stoCitaonice, klijent, pocetak, pocetak, status, pocetak);
		return id;
	}

	// Članstvo ide pravo u bazu: pozivnicu dokazuju njeni testovi, a ovdje je samo priprema
	private void clan(UUID organizacija, String email, String uloga) {
		jdbc.update("insert into organization_members select ?, id, ?, now() from users where email = ? "
				+ "on conflict do nothing", organizacija, uloga, email);
	}

	private void drugiSto() throws Exception {
		if (jdbc.queryForObject("select count(*) from resources where organization_id = ? and name = 'Sto 9'",
				Long.class, citaonica) == 0) {
			mockMvc.perform(post("/api/organizations/{id}/resources", citaonica)
					.header("Authorization", "Bearer " + damir)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"name": "Sto 9", "slotMinutes": 60}
							"""))
					.andExpect(status().isCreated());
		}
	}

	// GET slike za klijenta traži sliku, a u tom prolazu POST slike nije dozvoljen
	private void slikaPostoji() throws Exception {
		if (kljucSlike() == null) {
			mockMvc.perform(multipart("/api/resources/{id}/image", stoCitaonice)
					.file(new MockMultipartFile("file", "sto.png", "image/png", PNG))
					.header("Authorization", "Bearer " + alma))
					.andExpect(status().is2xxSuccessful());
		}
	}

	private String statusiPripremljenih() {
		return jdbc.queryForObject("select string_agg(status, ',' order by start_time) from bookings where id in "
				+ "(?, ?, ?, ?, ?, ?, ?)", String.class, rezervacije.values().toArray());
	}

	private String providerStola() {
		return jdbc.queryForObject("select u.email from resources r join users u on u.id = r.provider_id "
				+ "where r.id = ?", String.class, stoCitaonice);
	}

	private String statusiRezervacija() {
		return jdbc.queryForObject("select string_agg(status, ',' order by start_time) from bookings "
				+ "where resource_id = ?", String.class, stoCitaonice);
	}

	// Rezervacija traži radno vrijeme, a Almin PUT radnog vremena dolazi u listi tek poslije rezervacije
	private void ponedjeljkomOtvoreno() {
		jdbc.update("insert into availability_rules select gen_random_uuid(), ?, 1, '08:00', '20:00' "
				+ "where not exists (select 1 from availability_rules where resource_id = ?)", stoCitaonice,
				stoCitaonice);
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
					""".formatted(email, LOZINKA, ime))).andExpect(status().isAccepted());
			jdbc.update("update users set email_verified_at = now() where email = lower(?)", email);
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
		Map<String, Object> varijable = new HashMap<>(rezervacije);
		varijable.putAll(prijedlozi);
		varijable.putAll(Map.of("id", citaonica, "resourceId", stoCitaonice, "timeOffId", odmorStola, "waitlistId",
				alminUpis, "inviteId", otvorenaPozivnica, "linkId", pozivniLink, "screenId", ekran, "sessionId", razgovor, "lejlaId",
				users.findByEmail("lejla.izolacija@example.com").orElseThrow().getId(), "kenanId",
				users.findByEmail("kenan.izolacija@example.com").orElseThrow().getId()));
		URI adresa = UriComponentsBuilder.fromUriString(zahtjev.putanja()).buildAndExpand(varijable).toUri();
		if (Zahtjev.SLIKA.equals(zahtjev.tijelo())) {
			return mockMvc.perform(multipart(zahtjev.metoda(), adresa)
					.file(new MockMultipartFile("file", "sto.png", "image/png", PNG))
					.header("Authorization", "Bearer " + token))
					.andReturn().getResponse().getStatus();
		}
		MockHttpServletRequestBuilder builder = request(zahtjev.metoda(), adresa);
		if (token != null) {
			builder.header("Authorization", "Bearer " + token);
		}
		if (zahtjev.tijelo() != null) {
			String almaId = users.findByEmail("alma.izolacija@example.com").orElseThrow().getId().toString();
			String tijelo = zahtjev.tijelo().replace("{almaId}", almaId);
			if (tijelo.contains("{qrKod}")) {
				tijelo = tijelo.replace("{qrKod}", qrKod(rezervacije.get("zaQrDolazak")));
			}
			builder.contentType(MediaType.APPLICATION_JSON).content(tijelo);
		}
		return mockMvc.perform(builder).andReturn().getResponse().getStatus();
	}

	// Ispravan kod, da 403 dođe od autorizacije, a ne od pogrešnog koda. Daje ga Alma, jer je rezervacija njena.
	private String qrKod(UUID rezervacija) throws Exception {
		String link = JsonPath.read(mockMvc.perform(get("/api/bookings/{id}/check-in-link", rezervacija)
				.header("Authorization", "Bearer " + alma))
				.andReturn().getResponse().getContentAsString(), "$.url");
		return link.substring(link.indexOf("#k=") + 3);
	}

	private long brojRedova(String tabela) {
		return jdbc.queryForObject("select count(*) from " + tabela + " where organization_id = ?", Long.class,
				citaonica);
	}

	@Test
	void adminDrugeOrganizacijeDobija403NaSvakomEndpointuINistaSeNeMijenja() throws Exception {
		long clanovaPrije = brojRedova("organization_members");
		long pozivnicaPrije = brojRedova("organization_invites");
		long zapisaPrije = brojRedova("audit_log");
		long resursaPrije = brojRedova("resources");
		long pravilaPrije = brojPravila();
		long izuzetakaPrije = brojIzuzetaka();
		String slikaPrije = kljucSlike();
		long rezervacijaPrije = brojRezervacija();
		String statusiPrije = statusiRezervacija();
		String linkoviPrije = brojPovucenihLinkova();

		for (Zahtjev zahtjev : ZAHTJEVI) {
			assertThat(posalji(zahtjev, bojan)).as(zahtjev.ruta()).isEqualTo(403);
		}

		assertThat(brojPovucenihLinkova()).isEqualTo(linkoviPrije);
		assertThat(brojRedova("organization_members")).isEqualTo(clanovaPrije);
		assertThat(brojRedova("organization_invites")).isEqualTo(pozivnicaPrije);
		assertThat(brojRedova("audit_log")).isEqualTo(zapisaPrije);
		assertThat(brojRedova("resources")).isEqualTo(resursaPrije);
		assertThat(jdbc.queryForObject("select slot_minutes from resources where id = ?", Integer.class,
				stoCitaonice)).isEqualTo(120);
		assertThat(jdbc.queryForObject("select active from resources where id = ?", Boolean.class,
				stoCitaonice)).isTrue();
		assertThat(brojPravila()).isEqualTo(pravilaPrije);
		assertThat(brojIzuzetaka()).isEqualTo(izuzetakaPrije);
		assertThat(kljucSlike()).isEqualTo(slikaPrije);
		assertThat(brojRezervacija()).isEqualTo(rezervacijaPrije);
		assertThat(statusiRezervacija()).isEqualTo(statusiPrije);
		assertThat(statusAlminogUpisa()).isEqualTo("WAITING");
		assertThat(brojUplata()).isZero();
	}

	private long brojRezervacija() {
		return jdbc.queryForObject("select count(*) from bookings where resource_id = ?", Long.class, stoCitaonice);
	}

	private String kljucSlike() {
		return jdbc.queryForObject("select image_key from resources where id = ?", String.class, stoCitaonice);
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

	// Klijent i provider iz iste organizacije: svaka ruta je ili dozvoljena (2xx) ili 403, tačno po listi
	@ParameterizedTest
	@ValueSource(strings = { "klijent", "provider drugog stola" })
	void clanIsteOrganizacijeSmijeSamoOnoStoNjegovaUlogaDaje(String uloga) throws Exception {
		Set<String> smije = uloga.equals("klijent") ? KLIJENT_SMIJE : PROVIDER_DRUGOG_SMIJE;
		String token = uloga.equals("klijent") ? lejla : damir;
		slikaPostoji();
		long clanovaPrije = brojRedova("organization_members");
		long pozivnicaPrije = brojRedova("organization_invites");
		long zadnjiZapis = jdbc.queryForObject("select coalesce(max(id), 0) from audit_log", Long.class);
		long pravilaPrije = brojPravila();
		long izuzetakaPrije = brojIzuzetaka();
		String slikaPrije = kljucSlike();
		String statusiPrije = statusiPripremljenih();
		String linkoviPrije = brojPovucenihLinkova();

		for (Zahtjev zahtjev : ZAHTJEVI) {
			int status = posalji(zahtjev, token);
			if (smije.contains(zahtjev.ruta())) {
				assertThat(status).as(uloga + ": " + zahtjev.ruta()).isBetween(200, 299);
			} else {
				assertThat(status).as(uloga + ": " + zahtjev.ruta()).isEqualTo(403);
			}
		}

		assertThat(brojRedova("organization_members")).isEqualTo(clanovaPrije);
		assertThat(brojRedova("organization_invites")).isEqualTo(pozivnicaPrije);
		assertThat(brojPovucenihLinkova()).isEqualTo(linkoviPrije);
		// U dnevniku je samo ono što je uloga smjela: provider je napravio svoj resurs, klijent ništa
		assertThat(jdbc.queryForList("select action from audit_log where id > ? order by id", String.class,
				zadnjiZapis)).isEqualTo(uloga.equals("klijent") ? List.of() : List.of("RESOURCE_CREATED"));
		assertThat(jdbc.queryForObject("select slot_minutes from resources where id = ?", Integer.class,
				stoCitaonice)).isEqualTo(120);
		assertThat(jdbc.queryForObject("select active from resources where id = ?", Boolean.class,
				stoCitaonice)).isTrue();
		assertThat(providerStola()).isEqualTo("alma.izolacija@example.com");
		assertThat(brojPravila()).isEqualTo(pravilaPrije);
		assertThat(brojIzuzetaka()).isEqualTo(izuzetakaPrije);
		assertThat(kljucSlike()).isEqualTo(slikaPrije);
		assertThat(statusiPripremljenih()).isEqualTo(statusiPrije);
		assertThat(statusAlminogUpisa()).isEqualTo("WAITING");
		assertThat(brojUplata()).isZero();
	}

	// Lista uloga mora imenovati rute koje postoje, inače bi greška u kucanju tiho postala 403
	@Test
	void listeUlogaImenujuRuteIzTesta() {
		Set<String> uTestu = ZAHTJEVI.stream().map(Zahtjev::ruta).collect(Collectors.toSet());

		assertThat(uTestu).containsAll(KLIJENT_SMIJE).containsAll(PROVIDER_DRUGOG_SMIJE);
	}

	@Test
	void bezTokenaSvakaRutaVraca401() throws Exception {
		for (Zahtjev zahtjev : ZAHTJEVI) {
			assertThat(posalji(zahtjev, null)).as(zahtjev.ruta()).isEqualTo(401);
		}
	}

	// Bojan je admin teretane, pa @PreAuthorize za PUT .../provider s ID-jem teretane prođe. Sto čitaonice
	// u istoj putanji i dalje ne smije postati njegov, jer servis traži resurs unutar organizacije iz putanje.
	@Test
	void adminSaSvojomOrganizacijomITudjimResursomUPutanjiNistaNeMijenja() throws Exception {
		String bojanId = users.findByEmail("bojan.izolacija@example.com").orElseThrow().getId().toString();

		int noviProvider = mockMvc.perform(request(HttpMethod.PUT, "/api/organizations/{id}/resources/{r}/provider",
				teretana, stoCitaonice)
				.header("Authorization", "Bearer " + bojan)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"providerId": "%s"}
						""".formatted(bojanId)))
				.andReturn().getResponse().getStatus();
		int izmjena = mockMvc.perform(request(HttpMethod.PUT, "/api/organizations/{id}/resources/{r}", teretana,
				stoCitaonice)
				.header("Authorization", "Bearer " + bojan)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Sto za izolaciju", "slotMinutes": 30}
						"""))
				.andReturn().getResponse().getStatus();
		int deaktivacija = mockMvc.perform(request(HttpMethod.DELETE, "/api/organizations/{id}/resources/{r}",
				teretana, stoCitaonice)
				.header("Authorization", "Bearer " + bojan))
				.andReturn().getResponse().getStatus();

		assertThat(noviProvider).isEqualTo(404);
		assertThat(izmjena).isEqualTo(403);
		assertThat(deaktivacija).isEqualTo(403);
		assertThat(providerStola()).isEqualTo("alma.izolacija@example.com");
		assertThat(jdbc.queryForObject("select slot_minutes from resources where id = ?", Integer.class,
				stoCitaonice)).isEqualTo(120);
		assertThat(jdbc.queryForObject("select active from resources where id = ?", Boolean.class,
				stoCitaonice)).isTrue();
	}

	// Isto za članove i pozivnice: Bojan prođe @PreAuthorize sa svojom teretanom, ali Kenan, pozivnica i
	// link čitaonice se traže unutar teretane i ne nađu se
	@Test
	void adminSaSvojomOrganizacijomITudjimClanomIliPozivnicomUPutanjiNistaNeMijenja() throws Exception {
		UUID kenan = users.findByEmail("kenan.izolacija@example.com").orElseThrow().getId();
		long clanovaPrije = brojRedova("organization_members");
		long pozivnicaPrije = brojRedova("organization_invites");
		String linkoviPrije = brojPovucenihLinkova();

		assertThat(bojanovStatus(request(HttpMethod.DELETE, "/api/organizations/{id}/members/{u}", teretana, kenan))).isEqualTo(404);
		assertThat(bojanovStatus(request(HttpMethod.PUT, "/api/organizations/{id}/members/{u}/role", teretana, kenan)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"role": "ADMIN"}
						"""))).isEqualTo(404);
		assertThat(bojanovStatus(request(HttpMethod.DELETE, "/api/organizations/{id}/invites/{i}", teretana,
				otvorenaPozivnica))).isEqualTo(404);
		assertThat(bojanovStatus(request(HttpMethod.DELETE, "/api/organizations/{id}/invite-links/{l}", teretana,
				pozivniLink))).isEqualTo(404);
		assertThat(bojanovStatus(request(HttpMethod.DELETE, "/api/organizations/{id}/screens/{s}", teretana, ekran)))
				.isEqualTo(404);
		// Ekran svoje organizacije s tuđim resursom: termini čitaonice bi bili javni na Bojanovom ekranu
		assertThat(bojanovStatus(request(HttpMethod.POST, "/api/organizations/{id}/screens", teretana)
				.contentType(MediaType.APPLICATION_JSON).content("{\"resourceId\": \"" + stoCitaonice + "\"}")))
				.isEqualTo(404);

		assertThat(brojRedova("organization_members")).isEqualTo(clanovaPrije);
		assertThat(brojRedova("organization_invites")).isEqualTo(pozivnicaPrije);
		assertThat(brojPovucenihLinkova()).isEqualTo(linkoviPrije);
	}

	private int bojanovStatus(MockHttpServletRequestBuilder zahtjev) throws Exception {
		return mockMvc.perform(zahtjev.header("Authorization", "Bearer " + bojan)).andReturn().getResponse().getStatus();
	}

	// Čuvar: nova /api ruta ne može proći mimo ovog testa. Ili je u ZAHTJEVI, pa prolazi kroz sve uloge,
	// ili je u NISU_PODACI_ORGANIZACIJE s razlogom. Po obliku putanje se to ne može prepoznati: resurs i
	// rezervacija pripadaju organizaciji i kad je nema u putanji.
	@Test
	void svakaApiRutaJeUTestuIliImaRazlogZastoNije() {
		Set<String> uAplikaciji = new TreeSet<>();
		rute.getHandlerMethods().keySet().forEach(info -> info.getPatternValues().stream()
				.filter(putanja -> putanja.startsWith("/api/"))
				.forEach(putanja -> {
					var metode = info.getMethodsCondition().getMethods();
					// Mapiranje bez metode prima svaku metodu, a skup mu je prazan, pa bi ga forEach preskočio
					if (metode.isEmpty()) {
						uAplikaciji.add("SVE " + putanja);
					}
					metode.forEach(metoda -> uAplikaciji.add(metoda.name() + " " + putanja));
				}));

		Set<String> uTestu = ZAHTJEVI.stream().map(Zahtjev::ruta).collect(Collectors.toCollection(TreeSet::new));
		uTestu.addAll(NISU_PODACI_ORGANIZACIJE.keySet());

		assertThat(uAplikaciji).isNotEmpty().isEqualTo(uTestu);
	}

}
