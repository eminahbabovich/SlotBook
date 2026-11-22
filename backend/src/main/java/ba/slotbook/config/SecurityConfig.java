package ba.slotbook.config;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
// Bez ovoga se @PreAuthorize na kontroleru tiho ignoriše i endpoint je otvoren svakom prijavljenom
@EnableMethodSecurity
public class SecurityConfig {

	private static final String[] JAVNE_PUTANJE = {
			"/api/status",
			"/actuator/health/**",
			"/v3/api-docs/**",
			"/swagger-ui/**",
			"/swagger-ui.html"
	};

	private final HandlerExceptionResolver exceptionResolver;

	SecurityConfig(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
		this.exceptionResolver = exceptionResolver;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				// Access token ide u Authorization zaglavlje, koje browser ne šalje sam. Jedini kolačić je
				// refresh token (SameSite=Strict, samo /api/auth), a lažni zahtjev na /refresh napadaču ništa
				// ne daje jer odgovor ne može pročitati. Obrazloženje je u docs/ODLUKE.md.
				.csrf(csrf -> csrf.disable())
				// Pravila iz corsConfigurationSource ispod. Preflight (OPTIONS) nema token, pa ga CORS filter
				// mora obraditi prije provjere prijave, inače bi dobio 401.
				.cors(Customizer.withDefaults())
				// nosniff, X-Frame-Options: DENY i Cache-Control Spring Security šalje sam
				.headers(zaglavlja -> zaglavlja
						.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
						// Render završava HTTPS ispred aplikacije, pa je za Spring svaki zahtjev običan HTTP i
						// HSTS se po defaultu ne bi slao nikad. Browser HSTS preko HTTP-a ignoriše, pa lokalno
						// ne smeta.
						.httpStrictTransportSecurity(hsts -> hsts.requestMatcher(AnyRequestMatcher.INSTANCE))
						// API vraća JSON i ne treba ništa učitavati. Swagger UI učitava skripte, pa ovo ide
						// samo na /api.
						.addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
								PathPatternRequestMatcher.pathPattern("/api/**"),
								new StaticHeadersWriter("Content-Security-Policy",
										"default-src 'none'; frame-ancestors 'none'"))))
				.sessionManagement(sesija -> sesija.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(pravila -> pravila
						.requestMatchers(JAVNE_PUTANJE).permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login",
								"/api/auth/refresh", "/api/auth/logout", "/api/auth/verify-email", "/api/auth/email-verification",
								"/api/auth/password-reset", "/api/auth/password-reset/confirm").permitAll()
						// Javni profil organizacije: klijent treba vidjeti gdje rezerviše prije nego što se prijavi
						.requestMatchers(HttpMethod.GET, "/api/organizations/*").permitAll()
						// Pretplata na kalendar: Google ili telefon povlače fajl bez prijave, pravo daje token iz linka
						.requestMatchers(HttpMethod.GET, "/api/me/calendar.ics").permitAll()
						.anyRequest().authenticated())
				// Čita "Authorization: Bearer ..." i provjerava potpis i rok tokena preko JwtDecoder beana
				.oauth2ResourceServer(resourceServer -> resourceServer
						.jwt(Customizer.withDefaults())
						.authenticationEntryPoint(this::nijePrijavljen))
				.exceptionHandling(greske -> greske
						.authenticationEntryPoint(this::nijePrijavljen)
						.accessDeniedHandler((zahtjev, odgovor, izuzetak) ->
								exceptionResolver.resolveException(zahtjev, odgovor, null, izuzetak)));

		return http.build();
	}

	// Greške iz filtera nastaju prije kontrolera, pa ih ručno šaljemo u GlobalExceptionHandler,
	// da 401 ima isti Problem Details format kao sve ostale greške.
	private void nijePrijavljen(HttpServletRequest zahtjev, HttpServletResponse odgovor,
			AuthenticationException izuzetak) {
		odgovor.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		exceptionResolver.resolveException(zahtjev, odgovor, null, izuzetak);
	}

	// Samo adresa frontenda (ista kao u linkovima iz mailova). Kolačić s refresh tokenom ide samo uz
	// allowCredentials, a tada "*" nije dozvoljen ni u browseru.
	@Bean
	CorsConfigurationSource corsConfigurationSource(@Value("${slotbook.app-url}") String frontend) {
		CorsConfiguration pravila = new CorsConfiguration();
		pravila.setAllowedOrigins(List.of(frontend));
		pravila.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
		pravila.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
		pravila.setExposedHeaders(List.of("Retry-After", "Content-Disposition", "Location", "X-Request-Id"));
		pravila.setAllowCredentials(true);
		pravila.setMaxAge(Duration.ofHours(1));
		UrlBasedCorsConfigurationSource izvor = new UrlBasedCorsConfigurationSource();
		izvor.registerCorsConfiguration("/api/**", pravila);
		return izvor;
	}

	// Snaga je u application.properties (10). Testovi koriste 4, jer ne testiraju BCrypt nego tok,
	// osim VrijemeOdgovoraTest koji mjeri baš njega.
	@Bean
	PasswordEncoder passwordEncoder(@Value("${slotbook.bcrypt-strength}") int snaga) {
		return new BCryptPasswordEncoder(snaga);
	}

}
