package ba.slotbook.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RandomTokenTest {

	@Test
	void hashJeSha256UHexFormatu() {
		// Poznata SHA-256 vrijednost za "abc" iz standarda (FIPS 180-2)
		assertThat(RandomToken.sha256("abc"))
				.isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
	}

	@Test
	void tokenJe32BajtaUBase64BezZnakovaKojiSmetajuUUrlu() {
		// 32 bajta u Base64 bez dopune je 43 znaka, a URL varijanta koristi - i _ umjesto + i /
		assertThat(RandomToken.generate()).hasSize(43).matches("[A-Za-z0-9_-]+");
	}

}
