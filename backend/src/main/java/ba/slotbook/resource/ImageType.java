package ba.slotbook.resource;

import java.util.Arrays;
import java.util.Optional;

// Tip se čita iz prvih bajtova fajla, a ne iz imena ili Content-Type zaglavlja, jer to šalje
// klijent i može napisati šta hoće. Tako ni HTML preimenovan u .png ne prođe kao slika.
enum ImageType {

	JPEG("image/jpeg", "jpg"),
	PNG("image/png", "png"),
	WEBP("image/webp", "webp");

	private static final byte[] PNG_POTPIS = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };

	private final String contentType;
	private final String extension;

	ImageType(String contentType, String extension) {
		this.contentType = contentType;
		this.extension = extension;
	}

	static Optional<ImageType> detect(byte[] b) {
		if (b.length >= 3 && b[0] == (byte) 0xFF && b[1] == (byte) 0xD8 && b[2] == (byte) 0xFF) {
			return Optional.of(JPEG);
		}
		if (b.length >= 8 && Arrays.equals(b, 0, 8, PNG_POTPIS, 0, 8)) {
			return Optional.of(PNG);
		}
		// WebP: "RIFF", četiri bajta dužine, pa "WEBP"
		if (b.length >= 12 && pocinje(b, 0, "RIFF") && pocinje(b, 8, "WEBP")) {
			return Optional.of(WEBP);
		}
		return Optional.empty();
	}

	private static boolean pocinje(byte[] b, int od, String tekst) {
		for (int i = 0; i < tekst.length(); i++) {
			if (b[od + i] != tekst.charAt(i)) {
				return false;
			}
		}
		return true;
	}

	String contentType() {
		return contentType;
	}

	String extension() {
		return extension;
	}

}
