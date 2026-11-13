package ba.slotbook.storage;

import java.util.Optional;

// Interface jer stvarno postoje dvije implementacije: disk za lokalni rad i testove, i
// S3-kompatibilno skladište na produkciji, gdje Render nema trajni disk. Ključeve pravi
// aplikacija, nikad ne dolaze od korisnika.
public interface StorageService {

	void put(String key, byte[] content, String contentType);

	Optional<byte[]> get(String key);

	// Nepostojeći ključ nije greška: brisanje stare slike ne smije srušiti upload nove
	void delete(String key);

}
