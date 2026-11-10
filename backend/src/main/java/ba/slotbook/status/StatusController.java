package ba.slotbook.status;

import java.time.Clock;
import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/status")
public class StatusController {

	private final Clock clock;

	public StatusController(Clock clock) {
		this.clock = clock;
	}

	@GetMapping
	public StatusResponse status() {
		return new StatusResponse("slotbook", Instant.now(clock));
	}

}
