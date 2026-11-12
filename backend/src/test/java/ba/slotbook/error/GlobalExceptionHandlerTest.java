package ba.slotbook.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;

class GlobalExceptionHandlerTest {

	@Test
	void neocekivanaGreskaNeOtkrivaPorukuIzuzetka() {
		GlobalExceptionHandler handler = new GlobalExceptionHandler();

		ProblemDetail problem = handler.neocekivanaGreska(new RuntimeException("select * from users"));

		assertThat(problem.getStatus()).isEqualTo(500);
		assertThat(problem.getTitle()).isEqualTo("Greška na serveru");
		assertThat(problem.getDetail()).doesNotContain("select");
	}

	@Test
	void zabranaPristupaVraca403INeOtkrivaRazlog() {
		GlobalExceptionHandler handler = new GlobalExceptionHandler();

		ProblemDetail problem = handler.nemaPristup(new AccessDeniedException("nije član organizacije 42"));

		assertThat(problem.getStatus()).isEqualTo(403);
		assertThat(problem.getTitle()).isEqualTo("Pristup nije dozvoljen");
		assertThat(problem.getDetail()).doesNotContain("42");
	}

}
