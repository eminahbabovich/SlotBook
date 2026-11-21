package ba.slotbook.observability;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Svaki zahtjev dobije ID koji ide u svaku liniju loga (MDC) i u zaglavlje odgovora, pa se iz
// jednog odgovora nađu sve linije loga za taj zahtjev. Prije Spring Securityja, da ga ima i 401.
// ID se uvijek pravi ovdje: onaj iz zahtjeva bi klijent mogao iskoristiti da u log upiše šta hoće.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

	static final String ZAGLAVLJE = "X-Request-Id";
	static final String MDC_KLJUC = "requestId";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String id = UUID.randomUUID().toString();
		MDC.put(MDC_KLJUC, id);
		response.setHeader(ZAGLAVLJE, id);
		try {
			chain.doFilter(request, response);
		} finally {
			// Nit se vraća u pul i služi sljedeći zahtjev, pa ID ne smije ostati na njoj
			MDC.remove(MDC_KLJUC);
		}
	}

}
