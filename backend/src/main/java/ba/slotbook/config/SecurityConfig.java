package ba.slotbook.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
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
				// Token ide u Authorization zaglavlje, ne u kolačić, pa CSRF napad nema šta iskoristiti.
				// Ako refresh token završi u kolačiću, ovo se mora ponovo razmotriti za /api/auth/refresh.
				.csrf(csrf -> csrf.disable())
				.sessionManagement(sesija -> sesija.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(pravila -> pravila
						.requestMatchers(JAVNE_PUTANJE).permitAll()
						.anyRequest().authenticated())
				// Greške iz filtera nastaju prije kontrolera, pa ih ručno šaljemo u GlobalExceptionHandler,
				// da 401 i 403 imaju isti Problem Details format kao sve ostale greške.
				.exceptionHandling(greske -> greske
						.authenticationEntryPoint((zahtjev, odgovor, izuzetak) ->
								exceptionResolver.resolveException(zahtjev, odgovor, null, izuzetak))
						.accessDeniedHandler((zahtjev, odgovor, izuzetak) ->
								exceptionResolver.resolveException(zahtjev, odgovor, null, izuzetak)));

		return http.build();
	}

}
