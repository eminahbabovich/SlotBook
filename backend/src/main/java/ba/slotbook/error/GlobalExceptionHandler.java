package ba.slotbook.error;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<PoljeGreska> greske = ex.getBindingResult().getFieldErrors().stream()
				.map(greska -> new PoljeGreska(greska.getField(), greska.getDefaultMessage()))
				.toList();

		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"Neki podaci nisu ispravni. Ispravi označena polja i pokušaj ponovo.");
		problem.setTitle("Neispravni podaci");
		problem.setProperty("greske", greske);

		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	// Poruku izuzetka namjerno ne vraćamo klijentu: može sadržavati SQL, putanje ili imena klasa.
	@ExceptionHandler(Exception.class)
	ProblemDetail neocekivanaGreska(Exception ex) {
		log.error("Neočekivana greška", ex);

		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				"Nešto je pošlo po zlu na našoj strani. Pokušaj ponovo, a ako se ponovi, javi nam.");
		problem.setTitle("Greška na serveru");
		return problem;
	}

}
