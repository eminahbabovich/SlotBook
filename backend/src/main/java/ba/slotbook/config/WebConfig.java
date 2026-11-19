package ba.slotbook.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import ba.slotbook.ratelimit.AuthRateLimitInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final AuthRateLimitInterceptor granicaPokusaja;

	public WebConfig(AuthRateLimitInterceptor granicaPokusaja) {
		this.granicaPokusaja = granicaPokusaja;
	}

	// Interceptor, a ne filter: radi poslije Spring Securityja, ali prije kontrolera (prije BCrypt-a i
	// @Valid), a izuzetak iz njega ide kroz GlobalExceptionHandler kao svaka druga greška
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(granicaPokusaja).addPathPatterns("/api/auth/login", "/api/auth/register");
	}

}
