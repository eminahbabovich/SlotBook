package ba.slotbook.resource;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ba.slotbook.error.InvalidFieldException;
import ba.slotbook.storage.StorageService;

@Service
public class ResourceImageService {

	static final int NAJVISE_BAJTOVA = 2 * 1024 * 1024;

	private static final Logger log = LoggerFactory.getLogger(ResourceImageService.class);

	private final ResourceRepository resources;
	private final StorageService storage;

	public ResourceImageService(ResourceRepository resources, StorageService storage) {
		this.resources = resources;
		this.storage = storage;
	}

	// Bez @Transactional: skladište nije dio transakcije baze, pa je redoslijed namjeran. Prvo nova
	// slika, pa red u bazi, pa brisanje stare. Ako pukne baza, ostane slika koju niko ne koristi,
	// ali nikad resurs koji pokazuje na sliku koje nema.
	public ResourceResponse upload(UUID resourceId, byte[] sadrzaj) {
		ImageType tip = provjeri(sadrzaj);
		Resource resurs = resources.findById(resourceId).orElseThrow(ResourceNotFoundException::new);
		String stari = resurs.getImageKey();
		// Novi ključ za svaku sliku, da stara i nova nikad ne dijele isto ime
		String novi = "resources/" + resourceId + "/" + UUID.randomUUID() + "." + tip.extension();

		storage.put(novi, sadrzaj, tip.contentType());
		resurs.changeImage(novi);
		Resource sacuvan = resources.save(resurs);
		if (stari != null) {
			try {
				storage.delete(stari);
			} catch (RuntimeException e) {
				log.warn("Stara slika {} nije obrisana", stari, e);
			}
		}
		return ResourceResponse.from(sacuvan);
	}

	static ImageType provjeri(byte[] sadrzaj) {
		if (sadrzaj.length > NAJVISE_BAJTOVA) {
			throw new InvalidFieldException("file", "Slika može imati najviše 2 MB. Smanji je i pošalji ponovo.");
		}
		return ImageType.detect(sadrzaj).orElseThrow(() -> new InvalidFieldException("file",
				"Ovaj fajl nije slika koju primamo. Pošalji JPEG, PNG ili WebP."));
	}

	public ResourceImage image(UUID resourceId) {
		Resource resurs = resources.findById(resourceId).orElseThrow(ResourceNotFoundException::new);
		if (resurs.getImageKey() == null) {
			throw new ImageNotFoundException();
		}
		byte[] sadrzaj = storage.get(resurs.getImageKey()).orElseThrow(ImageNotFoundException::new);
		// Upisano je samo ono što je prošlo provjeri(), pa tip uvijek postoji
		return new ResourceImage(sadrzaj, ImageType.detect(sadrzaj).orElseThrow().contentType());
	}

	public record ResourceImage(byte[] content, String contentType) {
	}

}
