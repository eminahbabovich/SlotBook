package ba.slotbook.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.chat.ChatModel.Poruka;
import ba.slotbook.chat.ChatModel.PozivAlata;
import ba.slotbook.organization.OrganizationService;
import ba.slotbook.user.UserService;

// Amila je admin čitaonice, Lejla je klijent. Razgovor je Lejlin i ne vidi ga niko drugi, ni admin.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ChatSessionApiTest {

	private static final String LOZINKA = "razgovor-u-citaonici-3";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	UserService userService;

	@Autowired
	OrganizationService organizationService;

	@Autowired
	ChatSessionService sesije;

	@Value("${slotbook.ai.max-messages}")
	int maxPoruka;

	private final String sufiks = UUID.randomUUID().toString().substring(0, 6);
	private UUID amila;
	private UUID lejla;
	private UUID citaonica;

	@BeforeEach
	void citaonica() {
		amila = korisnik("amila", "Amila Begić");
		lejla = korisnik("lejla", "Lejla Šehić");
		citaonica = organizationService.create(amila, "Čitaonica FIT", "citaonica-chat-" + sufiks, "Europe/Sarajevo")
				.id();
		jdbc.update("insert into organization_members values (?, ?, 'CLIENT', now())", citaonica, lejla);
	}

	@Test
	void clanOtvaraSesijuIHistorijaJePrazna() throws Exception {
		String token = prijava("lejla");

		String tijelo = mockMvc.perform(post("/api/organizations/{id}/chat/sessions", citaonica)
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andReturn().getResponse().getContentAsString();

		mockMvc.perform(get("/api/chat/sessions/{id}/messages", (Object) JsonPath.read(tijelo, "$.id"))
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	// Pozivi alata i njihovi rezultati su za model. Na ekranu je samo ono što su napisali Lejla i asistent.
	@Test
	void historijaPokazujeSamoTekstKorisnikaIAsistentaPoRedu() throws Exception {
		UUID sesija = sesije.open(citaonica, lejla).id();
		sesije.dopisi(sesija, razgovor());

		mockMvc.perform(get("/api/chat/sessions/{id}/messages", sesija).header("Authorization", "Bearer " + prijava("lejla")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].role", contains("user", "assistant")))
				.andExpect(jsonPath("$[*].text", contains("Ima li slobodno mjesto u čitaonici sutra u 10?",
						"Ima, Sto 4 je slobodan od 10:00 do 12:00.")));
	}

	// Kroz jsonb i nazad, poziv alata i rezultat moraju ostati isti, inače ih provajder odbije
	@Test
	void modelDobijaPorukeTacnoKakoSuSacuvane() {
		UUID sesija = sesije.open(citaonica, lejla).id();
		sesije.dopisi(sesija, razgovor());

		assertThat(sesije.zaModel(sesija)).isEqualTo(razgovor());
	}

	@Test
	void tudjuSesijuNeCitaNiAdminIsteOrganizacije() throws Exception {
		UUID sesija = sesije.open(citaonica, lejla).id();
		sesije.dopisi(sesija, razgovor());

		mockMvc.perform(get("/api/chat/sessions/{id}/messages", sesija).header("Authorization", "Bearer " + prijava("amila")))
				.andExpect(status().isForbidden());
	}

	// Isto 403 kao za tuđu, da se po odgovoru ne vidi koje sesije postoje
	@Test
	void nepostojecaSesijaDajeIsto403() throws Exception {
		mockMvc.perform(get("/api/chat/sessions/{id}/messages", UUID.randomUUID())
				.header("Authorization", "Bearer " + prijava("lejla")))
				.andExpect(status().isForbidden());
	}

	@Test
	void korisnikIzTudjeOrganizacijeNeOtvaraSesiju() throws Exception {
		UUID bojan = korisnik("bojan", "Bojan Marić");
		organizationService.create(bojan, "Teretana Grbavica", "teretana-chat-" + sufiks, "Europe/Sarajevo");

		mockMvc.perform(post("/api/organizations/{id}/chat/sessions", citaonica)
				.header("Authorization", "Bearer " + prijava("bojan")).contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isForbidden());

		assertThat(jdbc.queryForObject("select count(*) from chat_sessions where organization_id = ?", Long.class,
				citaonica)).isZero();
	}

	// Sve ili ništa: poruke koje ne stanu ne upisuju se ni djelimično
	@Test
	void sesijaPrimaNajviseMaxPorukaIPrekoTogaNeUpiseNista() {
		UUID sesija = sesije.open(citaonica, lejla).id();
		sesije.dopisi(sesija, IntStream.range(1, maxPoruka).mapToObj(i -> Poruka.korisnik("Pitanje " + i)).toList());

		assertThatThrownBy(() -> sesije.dopisi(sesija, List.of(Poruka.korisnik("Još jedno"), Poruka.korisnik("I još"))))
				.isInstanceOf(ChatSessionFullException.class);
		assertThat(brojPoruka(sesija)).isEqualTo(maxPoruka - 1);

		sesije.dopisi(sesija, List.of(Poruka.korisnik("Zadnje koje stane")));
		assertThat(brojPoruka(sesija)).isEqualTo(maxPoruka);
	}

	@Test
	void clanKojiIzadjeIzOrganizacijeGubiRazgovore() {
		UUID sesija = sesije.open(citaonica, lejla).id();
		sesije.dopisi(sesija, razgovor());

		jdbc.update("delete from organization_members where organization_id = ? and user_id = ?", citaonica, lejla);

		assertThat(jdbc.queryForObject("select count(*) from chat_sessions where id = ?", Long.class, sesija)).isZero();
		assertThat(brojPoruka(sesija)).isZero();
	}

	// Bez lock-a na sesiji dvije niti pročitaju isti broj poruka i druga padne na primarnom ključu
	@Test
	void istovremenePorukeUIstojSesijiDobijajuRedneBrojeveBezRupa() throws Exception {
		UUID sesija = sesije.open(citaonica, lejla).id();
		int niti = 10;
		CountDownLatch start = new CountDownLatch(1);
		List<Future<?>> rezultati = new ArrayList<>();
		try (ExecutorService izvrsilac = Executors.newFixedThreadPool(niti)) {
			for (int i = 0; i < niti; i++) {
				String tekst = "Poruka " + i;
				rezultati.add(izvrsilac.submit(() -> {
					start.await();
					sesije.dopisi(sesija, List.of(Poruka.korisnik(tekst)));
					return null;
				}));
			}
			start.countDown();
			for (Future<?> rezultat : rezultati) {
				rezultat.get();
			}
		}

		assertThat(jdbc.queryForList("select seq from chat_messages where session_id = ? order by seq", Integer.class,
				sesija)).isEqualTo(IntStream.rangeClosed(1, niti).boxed().toList());
		assertThat(jdbc.queryForObject("select message_count from chat_sessions where id = ?", Integer.class, sesija))
				.isEqualTo(niti);
	}

	private static List<Poruka> razgovor() {
		var poziv = new PozivAlata("call_1", "function", new PozivAlata.Funkcija("find_free_slots",
				"{\"date\": \"2035-11-28\", \"time\": \"10:00\"}"));
		return Collections.unmodifiableList(List.of(
				Poruka.korisnik("Ima li slobodno mjesto u čitaonici sutra u 10?"),
				new Poruka("assistant", null, List.of(poziv), null),
				Poruka.rezultatAlata("call_1", "[{\"resurs\": \"Sto 4\", \"od\": \"10:00\", \"do\": \"12:00\"}]"),
				new Poruka("assistant", "Ima, Sto 4 je slobodan od 10:00 do 12:00.", null, null)));
	}

	private long brojPoruka(UUID sesija) {
		return jdbc.queryForObject("select count(*) from chat_messages where session_id = ?", Long.class, sesija);
	}

	private UUID korisnik(String ime, String imeIPrezime) {
		return userService.register(email(ime), LOZINKA, imeIPrezime).id();
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
		return ime + ".chat." + sufiks + "@example.com";
	}

}
