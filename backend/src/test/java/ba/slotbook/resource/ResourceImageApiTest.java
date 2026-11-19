package ba.slotbook.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.user.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ResourceImageApiTest {

	private static final String LOZINKA = "stari-most-1566-slika";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	private String lejla;
	private String tarik;
	private String amra;
	private UUID sala;

	// Lejla je admin sale za sastanke, Tarik provider, Amra klijent. Sala je nova u svakom testu.
	@BeforeEach
	void salaZaSastanke() throws Exception {
		lejla = token("lejla.slike@example.com", "Lejla");
		tarik = token("tarik.slike@example.com", "Tarik");
		amra = token("amra.slike@example.com", "Amra");

		var postojeca = jdbc.queryForList("select id from organizations where slug = 'sale-slike'", UUID.class);
		UUID organizacija;
		if (postojeca.isEmpty()) {
			String tijelo = mockMvc.perform(post("/api/organizations")
					.header("Authorization", "Bearer " + lejla)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"name": "Sale u Vijećnici", "slug": "sale-slike", "timezone": "Europe/Sarajevo"}
							"""))
					.andReturn().getResponse().getContentAsString();
			organizacija = UUID.fromString(JsonPath.read(tijelo, "$.id"));
		} else {
			organizacija = postojeca.getFirst();
		}
		for (String[] clan : new String[][] { { "tarik.slike@example.com", "PROVIDER" },
				{ "amra.slike@example.com", "CLIENT" } }) {
			jdbc.update("insert into organization_members values (?, ?, ?, now()) on conflict do nothing",
					organizacija, users.findByEmail(clan[0]).orElseThrow().getId(), clan[1]);
		}

		String tijelo = mockMvc.perform(post("/api/organizations/{id}/resources", organizacija)
				.header("Authorization", "Bearer " + lejla)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Mala sala", "slotMinutes": 60}
						"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		sala = UUID.fromString(JsonPath.read(tijelo, "$.id"));
	}

	private String token(String email, String ime) throws Exception {
		if (users.findByEmail(email).isEmpty()) {
			mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
					{"email": "%s", "password": "%s", "displayName": "%s"}
					""".formatted(email, LOZINKA, ime))).andExpect(status().isCreated());
			jdbc.update("update users set email_verified_at = now() where email = lower(?)", email);
		}
		String tijelo = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "%s"}
				""".formatted(email, LOZINKA))).andReturn().getResponse().getContentAsString();
		return JsonPath.read(tijelo, "$.accessToken");
	}

	private ResultActions posalji(String token, byte[] sadrzaj) throws Exception {
		return mockMvc.perform(multipart("/api/resources/{resourceId}/image", sala)
				.file(new MockMultipartFile("file", "sala.png", "image/png", sadrzaj))
				.header("Authorization", "Bearer " + token));
	}

	private ResultActions procitaj(String token) throws Exception {
		return mockMvc.perform(get("/api/resources/{resourceId}/image", sala).header("Authorization", "Bearer " + token));
	}

	private long fajlovaUSkladistu() throws Exception {
		Path folder = Path.of("target/test-uploads/resources", sala.toString());
		try (Stream<Path> fajlovi = Files.list(folder)) {
			return fajlovi.count();
		}
	}

	@Test
	void adminPostavljaSlikuAKlijentJeCitaSIstimBajtovimaITipom() throws Exception {
		posalji(lejla, ImageTypeTest.PNG)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hasImage").value(true));

		procitaj(amra)
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "image/png"))
				.andExpect(content().bytes(ImageTypeTest.PNG));
	}

	// Ime fajla je i dalje sala.png, a tip odgovora slijedi bajtove. Stara slika ne ostaje u skladištu.
	@Test
	void novaSlikaZamjenjujeStaruIStaraSeBriseIzSkladista() throws Exception {
		posalji(lejla, ImageTypeTest.PNG).andExpect(status().isOk());
		posalji(lejla, ImageTypeTest.JPEG).andExpect(status().isOk());

		procitaj(amra)
				.andExpect(header().string("Content-Type", "image/jpeg"))
				.andExpect(content().bytes(ImageTypeTest.JPEG));
		assertThat(fajlovaUSkladistu()).isEqualTo(1);
	}

	@Test
	void htmlSImenomPngPrevelikFajlIZahtjevBezFajlaVracaju400() throws Exception {
		posalji(lejla, "<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.greske[0].polje").value("file"));

		byte[] preveliko = Arrays.copyOf(ImageTypeTest.PNG, ResourceImageService.NAJVISE_BAJTOVA + 1);
		posalji(lejla, preveliko)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.greske[0].polje").value("file"));

		mockMvc.perform(multipart("/api/resources/{resourceId}/image", sala).header("Authorization", "Bearer " + lejla))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.greske[0].polje").value("file"))
				.andExpect(jsonPath("$.greske[0].poruka").value("Ovaj fajl je obavezan."));

		procitaj(lejla).andExpect(status().isNotFound());
	}

	// Sliku mijenja ko mijenja resurs: Tarik nije provider ove sale, a Amra je klijent
	@Test
	void klijentITrenerTudjegResursaNeMijenjajuSliku() throws Exception {
		posalji(tarik, ImageTypeTest.PNG).andExpect(status().isForbidden());
		posalji(amra, ImageTypeTest.PNG).andExpect(status().isForbidden());

		procitaj(amra)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Slika ne postoji"));
	}

}
