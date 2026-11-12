package ba.slotbook.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RefreshTokenServiceTest {

	@Test
	void hashJeSha256UHexFormatu() {
		// Poznata SHA-256 vrijednost za "abc" iz standarda (FIPS 180-2)
		assertThat(RefreshTokenService.hash("abc"))
				.isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
	}

	@Test
	void tokenJeIstekaoUTrenutkuIsteka() {
		Instant istice = Instant.parse("2026-12-12T10:00:00Z");
		RefreshToken token = new RefreshToken(UUID.randomUUID(), UUID.randomUUID(), "hash",
				istice.minusSeconds(60), istice);

		assertThat(token.isExpired(istice.minusSeconds(1))).isFalse();
		assertThat(token.isExpired(istice)).isTrue();
	}

}
