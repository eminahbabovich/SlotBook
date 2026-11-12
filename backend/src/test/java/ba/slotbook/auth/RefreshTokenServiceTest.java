package ba.slotbook.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RefreshTokenServiceTest {

	@Test
	void tokenJeIstekaoUTrenutkuIsteka() {
		Instant istice = Instant.parse("2026-12-12T10:00:00Z");
		RefreshToken token = new RefreshToken(UUID.randomUUID(), UUID.randomUUID(), "hash",
				istice.minusSeconds(60), istice);

		assertThat(token.isExpired(istice.minusSeconds(1))).isFalse();
		assertThat(token.isExpired(istice)).isTrue();
	}

}
