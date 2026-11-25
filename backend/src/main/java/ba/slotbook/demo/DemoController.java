package ba.slotbook.demo;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Prijava pita ovaj endpoint da li je demo. Van profila demo kontroler ne postoji, pa je odgovor 404
// i frontend ne pokazuje ništa. Tako lozinka ne mora biti i u frontendu.
@RestController
@Profile("demo")
class DemoController {

	@GetMapping("/api/demo")
	DemoResponse racuni() {
		return new DemoResponse(DemoPodaci.LOZINKA, DemoPodaci.RACUNI);
	}

}
