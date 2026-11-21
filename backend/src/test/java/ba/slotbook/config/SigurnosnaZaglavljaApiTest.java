package ba.slotbook.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import ba.slotbook.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SigurnosnaZaglavljaApiTest {

	// slotbook.app-url u testovima, isti kao lokalni Vite
	private static final String FRONTEND = "http://localhost:5173";

	@Autowired
	MockMvc mockMvc;

	private static void imaSigurnosnaZaglavlja(ResultActions odgovor) throws Exception {
		odgovor.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string("X-Frame-Options", "DENY"))
				.andExpect(header().string("Referrer-Policy", "no-referrer"))
				.andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
				.andExpect(header().string("Strict-Transport-Security", containsString("max-age=31536000")));
	}

	// Običan HTTP, kao što Render prosljeđuje aplikaciji iza svog proxyja: HSTS mora biti i tu
	@Test
	void apiOdgovorImaSigurnosnaZaglavljaIHstsIBezHttps() throws Exception {
		imaSigurnosnaZaglavlja(mockMvc.perform(get("/api/status")).andExpect(status().isOk()));
	}

	// 401 nastaje u filteru, prije kontrolera, pa zaglavlja moraju stići i tu
	@Test
	void greskaIzFilteraImaIstaZaglavlja() throws Exception {
		imaSigurnosnaZaglavlja(mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized()));
	}

	// Swagger UI učitava skripte i stilove, pa strogi CSP ide samo na /api
	@Test
	void swaggerNemaStrogiCsp() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(header().doesNotExist("Content-Security-Policy"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"));
	}

	@Test
	void preflightSAdreseFrontendaJeDozvoljenSKolacicem() throws Exception {
		mockMvc.perform(options("/api/auth/login")
						.header("Origin", FRONTEND)
						.header("Access-Control-Request-Method", "POST")
						.header("Access-Control-Request-Headers", "content-type"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", FRONTEND))
				.andExpect(header().string("Access-Control-Allow-Credentials", "true"));
	}

	@Test
	void preflightSTudjeAdreseSeOdbija() throws Exception {
		mockMvc.perform(options("/api/auth/login")
						.header("Origin", "https://slotbook-lazni.example")
						.header("Access-Control-Request-Method", "POST"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

	// Bez Access-Control-Expose-Headers JavaScript na drugom originu ne vidi Retry-After, ime CSV fajla ni ID zahtjeva
	@Test
	void frontendVidiRetryAfterImeFajlaIIdZahtjeva() throws Exception {
		mockMvc.perform(get("/api/status").header("Origin", FRONTEND))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", FRONTEND))
				.andExpect(header().string("Access-Control-Expose-Headers", containsString("Retry-After")))
				.andExpect(header().string("Access-Control-Expose-Headers", containsString("Content-Disposition")))
				.andExpect(header().string("Access-Control-Expose-Headers", containsString("X-Request-Id")));
	}

}
