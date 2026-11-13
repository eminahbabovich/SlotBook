package ba.slotbook.resource;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class ImageTypeTest {

	static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13 };
	static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16 };
	static final byte[] WEBP = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);

	@Test
	void prepoznajeJpegPngIWebpPoPrvimBajtovima() {
		assertThat(ImageType.detect(JPEG)).contains(ImageType.JPEG);
		assertThat(ImageType.detect(PNG)).contains(ImageType.PNG);
		assertThat(ImageType.detect(WEBP)).contains(ImageType.WEBP);
	}

	// Ime i Content-Type su klijentovi, pa test i ne zna za njih: odlučuju samo bajtovi
	@Test
	void htmlIRiffKojiNijeWebpNisuSlika() {
		assertThat(ImageType.detect("<html><script>".getBytes(StandardCharsets.UTF_8))).isEmpty();
		assertThat(ImageType.detect("RIFF\0\0\0\0WAVEfmt ".getBytes(StandardCharsets.ISO_8859_1))).isEmpty();
	}

	@Test
	void prazanIPrekratakFajlNisuSlika() {
		assertThat(ImageType.detect(new byte[0])).isEmpty();
		assertThat(ImageType.detect(new byte[] { (byte) 0x89, 'P', 'N' })).isEmpty();
	}

}
