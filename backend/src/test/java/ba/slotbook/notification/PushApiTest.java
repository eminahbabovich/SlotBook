package ba.slotbook.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.organization.OrganizationService;
import ba.slotbook.resource.ResourceService;
import ba.slotbook.user.UserService;

// Push servis je lažni HTTP server na localhostu: zapisuje šta je primio i vraća status koji test zada.
// Pretplate na njega se upisuju direktno u bazu, jer API (s pravom) prima samo adrese pravih push servisa.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PushApiTest {

	private static final String LOZINKA = "obavijesti-na-telefon";
	private static final String JAVNI_KLJUC;
	private static final String PRIVATNI_KLJUC;
	private static HttpServer pushServis;
	private static final List<Primljeno> PRIMLJENO = new CopyOnWriteArrayList<>();
	private static final Map<String, Integer> ODGOVOR = new ConcurrentHashMap<>();
	// Ključevi "browsera" po uređaju, da test može dešifrovati ono što je push servis primio
	private static final Map<String, Browser> BROWSERI = new ConcurrentHashMap<>();

	record Browser(KeyPair kljucevi, byte[] auth) {
	}

	record Primljeno(String putanja, Map<String, List<String>> zaglavlja, byte[] tijelo) {
	}

	static {
		KeyPair vapid = p256();
		JAVNI_KLJUC = b64(nekomprimovan((ECPublicKey) vapid.getPublic()));
		PRIVATNI_KLJUC = b64(bajtova32(((ECPrivateKey) vapid.getPrivate()).getS()));
	}

	@DynamicPropertySource
	static void kljucevi(DynamicPropertyRegistry registry) {
		registry.add("slotbook.push.vapid-public-key", () -> JAVNI_KLJUC);
		registry.add("slotbook.push.vapid-private-key", () -> PRIVATNI_KLJUC);
	}

	@BeforeAll
	static void pokreniPushServis() throws IOException {
		pushServis = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		pushServis.createContext("/", razmjena -> {
			String putanja = razmjena.getRequestURI().getPath();
			// HttpServer piše zaglavlja kao "Content-encoding", pa se za poređenje svode na mala slova
			Map<String, List<String>> zaglavlja = new java.util.HashMap<>();
			razmjena.getRequestHeaders().forEach((k, v) -> zaglavlja.put(k.toLowerCase(java.util.Locale.ROOT), v));
			PRIMLJENO.add(new Primljeno(putanja, zaglavlja,
					razmjena.getRequestBody().readAllBytes()));
			razmjena.sendResponseHeaders(ODGOVOR.getOrDefault(putanja, 201), -1);
			razmjena.close();
		});
		pushServis.start();
	}

	@AfterAll
	static void ugasiPushServis() {
		pushServis.stop(0);
	}

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	UserService userService;

	@Autowired
	NotificationService notificationService;

	@Autowired
	NotificationSender sender;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	ResourceService resourceService;

	@Autowired
	PlatformTransactionManager transakcije;

	private final String sufiks = UUID.randomUUID().toString().substring(0, 6);
	private UUID amila;
	private UUID lejla;
	private TransactionTemplate transakcija;

	@BeforeEach
	void dvijeOsobe() {
		transakcija = new TransactionTemplate(transakcije);
		PRIMLJENO.clear();
		ODGOVOR.clear();
		amila = userService.register(email("amila"), LOZINKA, "Amila").id();
		lejla = userService.register(email("lejla"), LOZINKA, "Lejla").id();
	}

	@Test
	void javniKljucJeOnajIzOkruzenja() throws Exception {
		mockMvc.perform(get("/api/push/public-key").header("Authorization", "Bearer " + prijava("amila")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.enabled").value(true))
				.andExpect(jsonPath("$.publicKey").value(JAVNI_KLJUC));
	}

	@Test
	void adresaKojaNijePushServisSeNePrima() throws Exception {
		mockMvc.perform(put("/api/me/push-subscriptions").header("Authorization", "Bearer " + prijava("amila"))
				.contentType(MediaType.APPLICATION_JSON).content(pretplata("http://169.254.169.254/latest/meta-data")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.greske[0].polje").value("endpoint"));
		assertThat(brojPretplata(amila)).isZero();
	}

	// Isti browser, isti endpoint: kad se na njemu prijavi Lejla, Amiline obavijesti tu više ne stižu
	@Test
	void istiBrowserPodDrugimNalogomPrelaziNaNjega() throws Exception {
		String endpoint = "https://fcm.googleapis.com/fcm/send/" + sufiks;
		for (String ko : List.of("amila", "lejla")) {
			mockMvc.perform(put("/api/me/push-subscriptions").header("Authorization", "Bearer " + prijava(ko))
					.contentType(MediaType.APPLICATION_JSON).content(pretplata(endpoint)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.device").value("Telefon, Chrome"));
		}

		assertThat(jdbc.queryForList("select user_id from push_subscriptions where endpoint = ?", UUID.class,
				endpoint)).containsExactly(lejla);
	}

	// Amilina sesija je istekla bez odjave, a na istom browseru se prijavi Lejla. Amilina obavijest koja još
	// čeka u redu ne smije stići na browser koji je sad Lejlin. Mail Amili ostaje.
	@Test
	void kadBrowserPredjeNaDrugiNalogObavijestiPrethodnogKojeCekajuSeBrisu() throws Exception {
		String endpoint = "https://fcm.googleapis.com/fcm/send/" + sufiks;
		mockMvc.perform(put("/api/me/push-subscriptions").header("Authorization", "Bearer " + prijava("amila"))
				.contentType(MediaType.APPLICATION_JSON).content(pretplata(endpoint))).andExpect(status().isOk());
		transakcija.executeWithoutResult(s -> notificationService.enqueue(amila, NotificationType.WAITLIST_OFFER,
				new EmailContent("Termin se oslobodio: Sto 4, tihi dio", "Termin je tvoj ako ga potvrdiš.")));
		assertThat(kanali(amila)).containsExactlyInAnyOrder("EMAIL", "PUSH");

		mockMvc.perform(put("/api/me/push-subscriptions").header("Authorization", "Bearer " + prijava("lejla"))
				.contentType(MediaType.APPLICATION_JSON).content(pretplata(endpoint))).andExpect(status().isOk());

		assertThat(kanali(amila)).containsExactly("EMAIL");
	}

	@Test
	void tudjiBrowserSeNeMozeNiUgasitiNiTestirati() throws Exception {
		String endpoint = pretplataUBazi(amila, "amilin-telefon");
		String lejlin = prijava("lejla");

		mockMvc.perform(delete("/api/me/push-subscriptions").param("endpoint", endpoint)
				.header("Authorization", "Bearer " + lejlin)).andExpect(status().isNoContent());
		mockMvc.perform(post("/api/me/push-subscriptions/test").header("Authorization", "Bearer " + lejlin)
				.contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\": \"" + endpoint + "\"}"))
				.andExpect(status().isNotFound());

		assertThat(brojPretplata(amila)).isEqualTo(1);
		assertThat(PRIMLJENO).isEmpty();
	}

	// Ponuda s liste čekanja ide mailom i na oba Amilina browsera. Telefon je primi, a laptop je u
	// međuvremenu odjavljen (410): ta pretplata nestaje zajedno sa svojom obavijesti.
	@Test
	void ponudaIdeNaSvakiUredjajA410BrisePretplatu() {
		String telefon = pretplataUBazi(amila, "telefon");
		String laptop = pretplataUBazi(amila, "laptop");
		ODGOVOR.put("/push/laptop", 410);

		transakcija.executeWithoutResult(s -> notificationService.enqueue(amila, NotificationType.WAITLIST_OFFER,
				new EmailContent("Termin se oslobodio: Sto 4, tihi dio", "Termin je tvoj ako ga potvrdiš.")));
		assertThat(kanali(amila)).containsExactlyInAnyOrder("EMAIL", "PUSH", "PUSH");
		posaljiSvePush();

		assertThat(PRIMLJENO).extracting(Primljeno::putanja).containsExactlyInAnyOrder("/push/telefon", "/push/laptop");
		Primljeno poruka = PRIMLJENO.stream().filter(p -> p.putanja().equals("/push/telefon")).findFirst().orElseThrow();
		assertThat(poruka.zaglavlja().get("authorization").getFirst()).startsWith("vapid t=").contains("k=" + JAVNI_KLJUC);
		assertThat(poruka.zaglavlja().get("content-encoding")).containsExactly("aes128gcm");
		assertThat(poruka.zaglavlja().get("ttl")).containsExactly(String.valueOf(2 * 24 * 60 * 60));
		// Tijelo je šifrovano za taj browser: naslov se ne vidi
		assertThat(new String(poruka.tijelo())).doesNotContain("Sto 4");

		assertThat(jdbc.queryForObject("select status from notifications where push_subscription_id = "
				+ "(select id from push_subscriptions where endpoint = ?)", String.class, telefon)).isEqualTo("SENT");
		assertThat(jdbc.queryForObject("select count(*) from push_subscriptions where endpoint = ?", Integer.class,
				laptop)).isZero();
		assertThat(kanali(amila)).containsExactlyInAnyOrder("EMAIL", "PUSH");
	}

	// Ono što browser radi s porukom, napisano po RFC 8291 i RFC 8292: dešifruje je svojim ključem i
	// tajnom auth, a push servis provjeri VAPID potpis javnim ključem servera. Ako ovo prođe, pravi
	// browser poruku može pročitati, a pravi push servis je prihvata.
	@Test
	void porukaSeDesifrujeKljucemBrowseraAVapidPotpisJeIspravan() throws Exception {
		pretplataUBazi(amila, "telefon");

		transakcija.executeWithoutResult(s -> notificationService.enqueue(amila, NotificationType.WAITLIST_OFFER,
				new EmailContent("Termin se oslobodio: Sto 4, tihi dio", "Termin je tvoj ako ga potvrdiš.")));
		posaljiSvePush();

		Primljeno poruka = PRIMLJENO.getFirst();
		String procitano = new String(WebPushProvjera.desifruj(poruka.tijelo(), BROWSERI.get("telefon").kljucevi(),
				BROWSERI.get("telefon").auth()), java.nio.charset.StandardCharsets.UTF_8);
		assertThat(JsonPath.<String>read(procitano, "$.title")).isEqualTo("Termin se oslobodio: Sto 4, tihi dio");
		assertThat(JsonPath.<String>read(procitano, "$.body")).startsWith("Termin s liste čekanja je tvoj");
		assertThat(JsonPath.<String>read(procitano, "$.url")).isEqualTo("/rezervacije");

		Map<String, Object> jwt = WebPushProvjera.provjeriVapid(poruka.zaglavlja().get("authorization").getFirst(),
				JAVNI_KLJUC);
		assertThat(jwt).containsEntry("aud", "http://127.0.0.1:" + pushServis.getAddress().getPort())
				.containsEntry("sub", "mailto:obavijesti@slotbook.local");
		assertThat(((Number) jwt.get("exp")).longValue()).isBetween(Instant.now().getEpochSecond(),
				Instant.now().plusSeconds(24 * 3600).getEpochSecond());
	}

	// Posao šalje podsjetnike kad stigne (Render zna spavati), pa podsjetnik ne smije reći "sutra" za termin
	// koji je za dva sata. Na telefon ide tekst maila, s danom i satom termina.
	@Test
	void podsjetnikNaTelefonuKazeKadaJeTerminANeSutra() throws Exception {
		pretplataUBazi(amila, "telefon");
		String tekst = "Podsjetnik za termin četvrtak, 19. novembar 2035. u 10:00 (Sto 4, tihi dio). Ako ne možeš doći, "
				+ "otkaži ga u aplikaciji, da ga može uzeti neko drugi.";

		UUID organizacija = organizationService.create(amila, "Čitaonica PMF", "citaonica-pmf-" + sufiks,
				"Europe/Sarajevo").id();
		UUID sto = resourceService.create(organizacija, amila, "Sto 4, tihi dio", null, 60, true, 0, 1, 0).id();
		UUID rezervacija = UUID.randomUUID();
		jdbc.update("insert into bookings (id, resource_id, client_id, start_time, end_time, status, created_at) "
				+ "values (?, ?, ?, '2035-11-19 09:00Z', '2035-11-19 10:00Z', 'CONFIRMED', now())", rezervacija, sto, amila);

		transakcija.executeWithoutResult(s -> notificationService.enqueueForBooking(amila,
				NotificationType.BOOKING_REMINDER, rezervacija, new EmailContent("Podsjetnik: Sto 4, tihi dio", tekst)));
		posaljiSvePush();

		String procitano = new String(WebPushProvjera.desifruj(PRIMLJENO.getFirst().tijelo(),
				BROWSERI.get("telefon").kljucevi(), BROWSERI.get("telefon").auth()), java.nio.charset.StandardCharsets.UTF_8);
		assertThat(JsonPath.<String>read(procitano, "$.body")).isEqualTo(tekst);
	}

	// Push servis privremeno ne radi: isti ponovni pokušaj kao za mail, za minutu
	@Test
	void pushServisKojiNeRadiDobijePonovniPokusajKaoMail() {
		pretplataUBazi(amila, "telefon");
		ODGOVOR.put("/push/telefon", 503);

		transakcija.executeWithoutResult(s -> notificationService.enqueue(amila, NotificationType.WAITLIST_OFFER,
				new EmailContent("Termin se oslobodio: Sto 4, tihi dio", "Termin je tvoj ako ga potvrdiš.")));
		posaljiSvePush();

		Map<String, Object> red = jdbc.queryForMap(
				"select status, attempts, last_error, next_attempt_at > now() as kasnije from notifications "
						+ "where user_id = ? and channel = 'PUSH'", amila);
		assertThat(red).containsEntry("status", "PENDING").containsEntry("attempts", 1)
				.containsEntry("last_error", "Push servis je vratio 503").containsEntry("kasnije", true);
	}

	// Lozinka promijenjena ili mail o nalogu: to ide samo mailom, ne na telefon
	@Test
	void obavijestKojaNijeZaTelefonIdeSamoMailom() {
		pretplataUBazi(amila, "telefon");

		transakcija.executeWithoutResult(s -> notificationService.enqueue(amila, NotificationType.ACCOUNT_EXISTS,
				new EmailContent("Već imaš nalog", "Prijavi se.")));

		assertThat(kanali(amila)).containsExactly("EMAIL");
	}

	@Test
	void probnaObavijestIdeOdmahANa410KazeDaUredjajNijePrijavljen() throws Exception {
		String telefon = pretplataUBazi(amila, "telefon");
		String laptop = pretplataUBazi(amila, "laptop");
		ODGOVOR.put("/push/laptop", 410);
		String amilin = prijava("amila");

		mockMvc.perform(post("/api/me/push-subscriptions/test").header("Authorization", "Bearer " + amilin)
				.contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\": \"" + telefon + "\"}"))
				.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/me/push-subscriptions/test").header("Authorization", "Bearer " + amilin)
				.contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\": \"" + laptop + "\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Uređaj nije prijavljen"));

		assertThat(PRIMLJENO).extracting(Primljeno::putanja).containsExactly("/push/telefon", "/push/laptop");
		assertThat(brojPretplata(amila)).isEqualTo(1);
	}

	@Test
	void brisanjeNalogaBrisePretplate() throws Exception {
		pretplataUBazi(amila, "telefon");

		mockMvc.perform(delete("/api/me").header("Authorization", "Bearer " + prijava("amila"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"password\": \"" + LOZINKA + "\"}"))
				.andExpect(status().isNoContent());

		assertThat(brojPretplata(amila)).isZero();
	}

	// Pošalje sve push obavijesti koje čekaju. Mail se pomjeri za dan, da ga test ne šalje.
	private void posaljiSvePush() {
		jdbc.update("update notifications set next_attempt_at = now() + interval '1 day' where channel = 'EMAIL'");
		while (sender.sendNext()) {
			// jedna po jedna, kao posao koji radi svakih 30 sekundi
		}
	}

	private List<String> kanali(UUID korisnik) {
		return jdbc.queryForList("select channel from notifications where user_id = ?", String.class, korisnik);
	}

	private int brojPretplata(UUID korisnik) {
		return jdbc.queryForObject("select count(*) from push_subscriptions where user_id = ?", Integer.class,
				korisnik);
	}

	private String pretplataUBazi(UUID korisnik, String uredjaj) {
		// Upit u adresi samo čini endpoint jedinstvenim po testu, a putanja kaže koji je uređaj
		String endpoint = "http://127.0.0.1:" + pushServis.getAddress().getPort() + "/push/" + uredjaj + "?t=" + sufiks;
		KeyPair browser = p256();
		byte[] auth = new byte[16];
		new SecureRandom().nextBytes(auth);
		BROWSERI.put(uredjaj, new Browser(browser, auth));
		jdbc.update("insert into push_subscriptions values (?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), korisnik,
				endpoint, b64(nekomprimovan((ECPublicKey) browser.getPublic())), b64(auth), uredjaj,
				java.sql.Timestamp.from(Instant.now()));
		return endpoint;
	}

	private String pretplata(String endpoint) {
		KeyPair browser = p256();
		return """
				{"endpoint": "%s", "keys": {"p256dh": "%s", "auth": "%s"}, "device": "Telefon, Chrome"}
				""".formatted(endpoint, b64(nekomprimovan((ECPublicKey) browser.getPublic())), b64(new byte[16]));
	}

	private String prijava(String ime) throws Exception {
		jdbc.update("update users set email_verified_at = now() where email = ?", email(ime));
		String tijelo = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "%s"}
				""".formatted(email(ime), LOZINKA))).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
		return JsonPath.read(tijelo, "$.accessToken");
	}

	private String email(String ime) {
		return ime + ".push." + sufiks + "@example.com";
	}

	private static KeyPair p256() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
			generator.initialize(new ECGenParameterSpec("secp256r1"));
			return generator.generateKeyPair();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	// 0x04, pa x i y po 32 bajta: oblik u kome browser i VAPID šalju javni ključ
	private static byte[] nekomprimovan(ECPublicKey kljuc) {
		byte[] x = bajtova32(kljuc.getW().getAffineX());
		byte[] y = bajtova32(kljuc.getW().getAffineY());
		byte[] rezultat = new byte[65];
		rezultat[0] = 4;
		System.arraycopy(x, 0, rezultat, 1, 32);
		System.arraycopy(y, 0, rezultat, 33, 32);
		return rezultat;
	}

	private static byte[] bajtova32(BigInteger broj) {
		byte[] bajtovi = broj.toByteArray();
		if (bajtovi.length == 32) {
			return bajtovi;
		}
		byte[] rezultat = new byte[32];
		int duzina = Math.min(bajtovi.length, 32);
		System.arraycopy(bajtovi, bajtovi.length - duzina, rezultat, 32 - duzina, duzina);
		return rezultat;
	}

	private static String b64(byte[] bajtovi) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(bajtovi, bajtovi.length));
	}

}
