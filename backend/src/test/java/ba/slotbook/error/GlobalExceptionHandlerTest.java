package ba.slotbook.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

class GlobalExceptionHandlerTest {

	@Test
	void neocekivanaGreskaNeOtkrivaPorukuIzuzetka() {
		GlobalExceptionHandler handler = new GlobalExceptionHandler();

		ProblemDetail problem = handler.neocekivanaGreska(new RuntimeException("select * from users"));

		assertThat(problem.getStatus()).isEqualTo(500);
		assertThat(problem.getTitle()).isEqualTo("Greška na serveru");
		assertThat(problem.getDetail()).doesNotContain("select");
	}

}
