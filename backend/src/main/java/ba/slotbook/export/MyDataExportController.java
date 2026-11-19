package ba.slotbook.export;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MyDataExportController {

	private final MyDataExportService exportService;

	public MyDataExportController(MyDataExportService exportService) {
		this.exportService = exportService;
	}

	// Korisnik iz tokena, nikad iz putanje: tuđi izvoz se ne može ni zatražiti
	@GetMapping("/api/me/export")
	public MyDataExport export(@AuthenticationPrincipal Jwt token) {
		return exportService.export(UUID.fromString(token.getSubject()));
	}

}
