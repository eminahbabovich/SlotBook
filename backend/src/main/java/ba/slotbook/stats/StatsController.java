package ba.slotbook.stats;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatsController {

	private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

	private final StatsService statsService;

	public StatsController(StatsService statsService) {
		this.statsService = statsService;
	}

	// Klijent statistiku ne vidi. Šta vidi provider, a šta admin, odlučuje servis po ulozi iz baze.
	@GetMapping(path = "/api/organizations/{id}/stats", produces = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("@organizationAccess.hasRole(#id, authentication, 'ADMIN', 'PROVIDER')")
	public StatsResponse stats(@PathVariable UUID id, @AuthenticationPrincipal Jwt token,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return statsService.stats(id, UUID.fromString(token.getSubject()), from, to);
	}

	// Isti podaci, isti resurs, samo drugi oblik: bira ga zaglavlje Accept: text/csv. U CSV ide
	// popunjenost po resursu, jer je to tabela koja se nosi u Excel.
	@GetMapping(path = "/api/organizations/{id}/stats", produces = "text/csv")
	@PreAuthorize("@organizationAccess.hasRole(#id, authentication, 'ADMIN', 'PROVIDER')")
	public ResponseEntity<String> statsCsv(@PathVariable UUID id, @AuthenticationPrincipal Jwt token,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		StatsResponse statistika = statsService.stats(id, UUID.fromString(token.getSubject()), from, to);
		ContentDisposition fajl = ContentDisposition.attachment()
				.filename("popunjenost-" + from + "-" + to + ".csv")
				.build();
		return ResponseEntity.ok()
				.contentType(CSV)
				.header(HttpHeaders.CONTENT_DISPOSITION, fajl.toString())
				.body(OccupancyCsv.write(statistika.occupancy()));
	}

}
