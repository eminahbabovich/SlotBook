package ba.slotbook.error;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import ba.slotbook.auth.InvalidRefreshTokenException;
import ba.slotbook.availability.TimeOffNotFoundException;
import ba.slotbook.booking.BookingStatus;
import ba.slotbook.booking.BookingTransitionException;
import ba.slotbook.booking.CancelDeadlinePassedException;
import ba.slotbook.booking.IdempotencyKeyReusedException;
import ba.slotbook.booking.NoShowTooEarlyException;
import ba.slotbook.booking.SlotTakenException;
import ba.slotbook.organization.AlreadyMemberException;
import ba.slotbook.organization.InviteRaceException;
import ba.slotbook.auth.UserTokenException;
import ba.slotbook.organization.InviteNotUsableException;
import ba.slotbook.organization.OrganizationNotFoundException;
import ba.slotbook.organization.SlugAlreadyUsedException;
import ba.slotbook.ratelimit.TooManyAttemptsException;
import ba.slotbook.resource.ImageNotFoundException;
import ba.slotbook.resource.ResourceNotFoundException;
import ba.slotbook.user.EmailAlreadyUsedException;

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

	// Parametri iz adrese, npr. ?from= kod slobodnih termina. Bez ovoga Spring vraća 400 s porukom na engleskom.
	@Override
	protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return handleExceptionInternal(ex, neispravnoPolje(ex.getParameterName(), "Ovaj parametar je obavezan."),
				headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		String poruka = ex.getRequiredType() == LocalDate.class
				? "Datum se piše kao godina-mjesec-dan, npr. 2026-11-16."
				: "Vrijednost nije u ispravnom obliku.";
		return handleExceptionInternal(ex, neispravnoPolje(ex.getPropertyName(), poruka), headers, status, request);
	}

	// Fajl iz multipart zahtjeva, npr. slika resursa poslana bez polja file
	@Override
	protected ResponseEntity<Object> handleMissingServletRequestPart(MissingServletRequestPartException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return handleExceptionInternal(ex, neispravnoPolje(ex.getRequestPartName(), "Ovaj fajl je obavezan."),
				headers, status, request);
	}

	// Ovo baca Tomcat kad zahtjev pređe spring.servlet.multipart granicu, prije nego što dođe do servisa
	@Override
	protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE,
				"Fajl je prevelik. Slika može imati najviše 2 MB.");
		problem.setTitle("Fajl je prevelik");
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	@ExceptionHandler(ImageNotFoundException.class)
	ProblemDetail slikaNePostoji(ImageNotFoundException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
				"Ovaj resurs još nema sliku.");
		problem.setTitle("Slika ne postoji");
		return problem;
	}

	// Retry-After u sekundama, da klijent zna koliko da čeka bez čitanja teksta
	@ExceptionHandler(TooManyAttemptsException.class)
	ResponseEntity<ProblemDetail> previsePokusaja(TooManyAttemptsException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, switch (ex.getRazlog()) {
			case ADRESA -> "Previše pokušaja s ove adrese. Sačekaj malo pa pokušaj ponovo.";
			// Isti tekst i za email bez naloga, da se po poruci ne vidi ko ima nalog
			case NALOG -> "Za ovaj email je bilo previše pogrešnih lozinki, pa je prijava zaključana na 15 minuta. "
					+ "Pokušaj ponovo kasnije.";
			case POZIVNICA -> "Na ovaj email su u zadnjih sat vremena poslane tri pozivnice. Pokušaj ponovo kasnije.";
		});
		problem.setTitle(switch (ex.getRazlog()) {
			case ADRESA -> "Previše pokušaja";
			case NALOG -> "Prijava privremeno zaključana";
			case POZIVNICA -> "Previše mailova";
		});
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.sekundiDoSljedeceg()))
				.body(problem);
	}

	@ExceptionHandler(EmailAlreadyUsedException.class)
	ProblemDetail emailZauzet(EmailAlreadyUsedException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"Nalog s ovim emailom već postoji. Prijavi se ili koristi drugi email.");
		problem.setTitle("Email je već registrovan");
		return problem;
	}

	@ExceptionHandler(SlotTakenException.class)
	ProblemDetail terminZauzet(SlotTakenException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"Neko je upravo rezervisao ovaj termin. Izaberi drugi slobodan termin.");
		problem.setTitle("Termin je zauzet");
		return problem;
	}

	// Stanje ide i kao polje, da frontend može osvježiti prikaz bez parsiranja teksta.
	// Ne pod imenom "status": properties se ispisuju na vrhu JSON-a (@JsonAnyGetter) i
	// pregazili bi HTTP status koji Problem Details tu propisuje.
	@ExceptionHandler(BookingTransitionException.class)
	ProblemDetail prelazNijeDozvoljen(BookingTransitionException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"Ova promjena nije moguća jer je rezervacija " + opis(ex.getFrom()) + ". Osvježi prikaz.");
		problem.setTitle("Promjena nije moguća");
		problem.setProperty("trenutniStatus", ex.getFrom());
		problem.setProperty("trazeniStatus", ex.getTo());
		return problem;
	}

	private static String opis(BookingStatus status) {
		return switch (status) {
			case PENDING -> "na čekanju";
			case CONFIRMED -> "potvrđena";
			case REJECTED -> "odbijena";
			case EXPIRED -> "istekla";
			case CANCELLED -> "otkazana";
			case COMPLETED -> "završena";
			case NO_SHOW -> "označena kao nedolazak";
		};
	}

	// Rok ide i kao polje, da ga frontend prikaže u zoni organizacije
	@ExceptionHandler(CancelDeadlinePassedException.class)
	ProblemDetail rokZaOtkazivanjeProsao(CancelDeadlinePassedException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"Ovaj termin se može otkazati najkasnije " + ex.getNoticeHours()
						+ " h prije početka. Ako ne možeš doći, javi se osobi koja vodi termin.");
		problem.setTitle("Rok za otkazivanje je prošao");
		problem.setProperty("rokZaOtkazivanje", ex.getDeadline());
		return problem;
	}

	@ExceptionHandler(NoShowTooEarlyException.class)
	ProblemDetail nedolazakPrerano(NoShowTooEarlyException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"Nedolazak se može označiti tek kad termin počne.");
		problem.setTitle("Termin još nije počeo");
		return problem;
	}

	// @Version: neko je istu rezervaciju promijenio između čitanja i upisa
	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	ProblemDetail istovremenaIzmjena(ObjectOptimisticLockingFailureException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"Rezervaciju je u međuvremenu promijenio neko drugi. Osvježi prikaz i pokušaj ponovo.");
		problem.setTitle("Rezervacija je promijenjena");
		return problem;
	}

	@ExceptionHandler(IdempotencyKeyReusedException.class)
	ProblemDetail kljucIskoristen(IdempotencyKeyReusedException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT,
				"Ovaj Idempotency-Key je već poslan uz drugu rezervaciju. Za novu rezervaciju pošalji novi ključ.");
		problem.setTitle("Ključ je već iskorišten");
		return problem;
	}

	@ExceptionHandler(SlugAlreadyUsedException.class)
	ProblemDetail slugZauzet(SlugAlreadyUsedException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"Ovu adresu već koristi druga organizacija. Izaberi drugu.");
		problem.setTitle("Adresa je zauzeta");
		return problem;
	}

	@ExceptionHandler(InviteRaceException.class)
	ProblemDetail pozivnicaUIstomTrenutku(InviteRaceException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"Neko je u istom trenutku poslao pozivnicu na ovaj email, pa je jedna već poslana. Druga ne treba.");
		problem.setTitle("Pozivnica je upravo poslana");
		return problem;
	}

	@ExceptionHandler(AlreadyMemberException.class)
	ProblemDetail vecClan(AlreadyMemberException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"Osoba s ovim emailom je već član organizacije i nalazi se na listi članova.");
		problem.setTitle("Već je član");
		return problem;
	}

	@ExceptionHandler(InviteNotUsableException.class)
	ProblemDetail pozivnicaNeVazi(InviteNotUsableException ex) {
		ProblemDetail problem = switch (ex.getReason()) {
			case NOT_FOUND -> ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
					"Pozivnica s ovim linkom ne postoji. Možda je admin u međuvremenu poslao novu, provjeri zadnji link.");
			case OTHER_EMAIL -> ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
					"Pozivnica je poslana na drugi email. Prijavi se nalogom s tim emailom.");
			case ALREADY_USED -> ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
					"Ova pozivnica je već iskorištena.");
			case EXPIRED -> ProblemDetail.forStatusAndDetail(HttpStatus.GONE,
					"Pozivnica je istekla. Zamoli admina organizacije da ti pošalje novu.");
		};
		problem.setTitle("Pozivnica ne važi");
		return problem;
	}

	@ExceptionHandler(UserTokenException.class)
	ProblemDetail linkIzMailaNeVazi(UserTokenException ex) {
		return switch (ex.getType()) {
			case EMAIL_VERIFY -> linkNeVazi(ex.getReason(), "Link za potvrdu ne važi",
					"Link za potvrdu emaila ne postoji. Provjeri da li je kopiran cijeli link iz maila.",
					"Ovaj link je već iskorišten. Ako email još nije potvrđen, zatraži novi link na stranici za prijavu.",
					"Link za potvrdu emaila je istekao. Zatraži novi link na stranici za prijavu.");
			// Iskorišten je i kad je lozinka promijenjena drugim, novijim linkom
			case PASSWORD_RESET -> linkNeVazi(ex.getReason(), "Link za novu lozinku ne važi",
					"Link za novu lozinku ne postoji. Provjeri da li je kopiran cijeli link iz maila.",
					"Lozinka je već promijenjena. Prijavi se novom lozinkom ili zatraži novi link.",
					"Link za novu lozinku je istekao. Zatraži novi link na stranici za prijavu.");
		};
	}

	private static ProblemDetail linkNeVazi(UserTokenException.Reason razlog, String naslov, String nePostoji,
			String iskoristen, String istekao) {
		ProblemDetail problem = switch (razlog) {
			case NOT_FOUND -> ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, nePostoji);
			case ALREADY_USED -> ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, iskoristen);
			case EXPIRED -> ProblemDetail.forStatusAndDetail(HttpStatus.GONE, istekao);
		};
		problem.setTitle(naslov);
		return problem;
	}

	@ExceptionHandler(OrganizationNotFoundException.class)
	ProblemDetail organizacijaNePostoji(OrganizationNotFoundException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
				"Organizacija s ovom adresom ne postoji. Provjeri da li je adresa ispravno napisana.");
		problem.setTitle("Organizacija ne postoji");
		return problem;
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	ProblemDetail resursNePostoji(ResourceNotFoundException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
				"Resurs ne postoji u ovoj organizaciji. Osvježi listu resursa i pokušaj ponovo.");
		problem.setTitle("Resurs ne postoji");
		return problem;
	}

	// Isti oblik kao greška validacije, da frontend grešku prikaže uz polje
	@ExceptionHandler(InvalidFieldException.class)
	ProblemDetail neispravnoPolje(InvalidFieldException ex) {
		return neispravnoPolje(ex.getPolje(), ex.getPoruka());
	}

	private static ProblemDetail neispravnoPolje(String polje, String poruka) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"Neki podaci nisu ispravni. Ispravi označena polja i pokušaj ponovo.");
		problem.setTitle("Neispravni podaci");
		problem.setProperty("greske", List.of(new PoljeGreska(polje, poruka)));
		return problem;
	}

	@ExceptionHandler(TimeOffNotFoundException.class)
	ProblemDetail izuzetakNePostoji(TimeOffNotFoundException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
				"Ovaj izuzetak ne postoji za ovaj resurs. Možda je već obrisan, osvježi listu.");
		problem.setTitle("Izuzetak ne postoji");
		return problem;
	}

	@ExceptionHandler(InvalidRefreshTokenException.class)
	ProblemDetail sesijaIstekla(InvalidRefreshTokenException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
				"Sesija je istekla ili je prekinuta. Prijavi se ponovo.");
		problem.setTitle("Sesija je istekla");
		return problem;
	}

	// Ista poruka za nepoznat email i pogrešnu lozinku, da se ne može saznati ko ima nalog
	@ExceptionHandler(BadCredentialsException.class)
	ProblemDetail pogresniPodaciZaPrijavu(BadCredentialsException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
				"Email ili lozinka nisu tačni. Provjeri ih i pokušaj ponovo.");
		problem.setTitle("Prijava nije uspjela");
		return problem;
	}

	@ExceptionHandler(AuthenticationException.class)
	ProblemDetail nijePrijavljen(AuthenticationException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
				"Za ovo se treba prijaviti. Prijavi se i pokušaj ponovo.");
		problem.setTitle("Prijava je potrebna");
		return problem;
	}

	// Bez ovoga bi zabrana iz @PreAuthorize završila u handleru ispod kao 500
	@ExceptionHandler(AccessDeniedException.class)
	ProblemDetail nemaPristup(AccessDeniedException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
				"Nemaš pristup ovome. Ako misliš da bi trebalo, javi se administratoru organizacije.");
		problem.setTitle("Pristup nije dozvoljen");
		return problem;
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
