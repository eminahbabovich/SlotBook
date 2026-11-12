package ba.slotbook.config;

import org.springframework.beans.factory.annotation.Qualifier;
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
				.sessionManagement(sesija -> sesija.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(pravila -> pravila
						.requestMatchers(JAVNE_PUTANJE).permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login",
								"/api/auth/refresh", "/api/auth/logout").permitAll()
						// Javni profil organizacije: klijent treba vidjeti gdje rezerviše prije nego što se prijavi
						.requestMatchers(HttpMethod.GET, "/api/organizations/*").permitAll()
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

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
