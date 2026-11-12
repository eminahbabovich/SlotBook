package ba.slotbook.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import ba.slotbook.TestcontainersConfiguration;
import ba.slotbook.user.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class LoginApiTest {

	private static final String EMAIL = "nermin.login@example.com";
	private static final String LOZINKA = "vijecnica1896";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository users;

	@Autowired
	JwtEncoder jwtEncoder;

	@BeforeEach
	void korisnikPostoji() throws Exception {
		if (users.findByEmail(EMAIL).isEmpty()) {
			mockMvc.perform(post("/api/auth/register")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"email": "%s", "password": "%s", "displayName": "Nermin"}
							""".formatted(EMAIL, LOZINKA)))
					.andExpect(status().isCreated());
		}
	}

	private ResultActions prijava(String email, String lozinka) throws Exception {
		return mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "%s", "password": "%s"}
						""".formatted(email, lozinka)));
	}

	private String token() throws Exception {
		String tijelo = prijava(EMAIL, LOZINKA).andReturn().getResponse().getContentAsString();
		return JsonPath.read(tijelo, "$.accessToken");
	}

	private ResultActions me(String token) throws Exception {
		return mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token));
	}

	@Test
	void tokenIzPrijaveOtvaraApiMeSaMojimPodacima() throws Exception {
		prijava(EMAIL, LOZINKA)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresIn").value(900));

		me(token())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(EMAIL))
				.andExpect(jsonPath("$.displayName").value("Nermin"));
	}

	@Test
	void pogresnaLozinkaINepoznatEmailDajuIstiOdgovor() throws Exception {
		String pogresnaLozinka = prijava(EMAIL, "pogresna123")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.title").value("Prijava nije uspjela"))
				.andReturn().getResponse().getContentAsString();

		prijava("ne.postoji@example.com", "pogresna123")
				.andExpect(status().isUnauthorized())
				.andExpect(content().json(pogresnaLozinka));
	}

	@Test
	void apiMeBezTokenaVraca401SaBearerZaglavljem() throws Exception {
		mockMvc.perform(get("/api/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Bearer"))
				.andExpect(jsonPath("$.title").value("Prijava je potrebna"));
	}

	@Test
	void izmijenjenTokenSeOdbija() throws Exception {
		String[] dijelovi = token().split("\\.");
		char[] potpis = dijelovi[2].toCharArray();
		potpis[5] = potpis[5] == 'A' ? 'B' : 'A';

		me(dijelovi[0] + "." + dijelovi[1] + "." + new String(potpis))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"));
	}

	@Test
	void istekaoTokenSeOdbija() throws Exception {
		Instant prijeSat = Instant.now().minusSeconds(3600);
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(users.findByEmail(EMAIL).orElseThrow().getId().toString())
				.issuedAt(prijeSat)
				.expiresAt(prijeSat.plusSeconds(900))
				.build();
		String istekao = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();

		me(istekao).andExpect(status().isUnauthorized());
	}

}
