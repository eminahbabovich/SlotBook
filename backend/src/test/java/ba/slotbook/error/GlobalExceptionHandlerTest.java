package ba.slotbook.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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

	// Granicu iz spring.servlet.multipart primjenjuje Tomcat, a MockMvc je zaobilazi. Zato se handler
	// zove direktno: ovo dokazuje oblik odgovora, ali ne i da Tomcat stvarno odbije fajl od 3 MB.
	@Test
	void prevelikFajlVraca413SPorukomNaBosanskom() {
		ResponseEntity<Object> odgovor = new GlobalExceptionHandler().handleMaxUploadSizeExceededException(
				new MaxUploadSizeExceededException(2 * 1024 * 1024), new HttpHeaders(), HttpStatus.CONTENT_TOO_LARGE,
				new ServletWebRequest(new MockHttpServletRequest()));

		assertThat(odgovor.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
		assertThat(((ProblemDetail) odgovor.getBody()).getDetail()).contains("najviše 2 MB");
	}

}
