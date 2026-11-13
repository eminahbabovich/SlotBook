package ba.slotbook.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// Podrazumijevano skladište: bez slotbook.storage.type ili s vrijednošću local
@Component
@ConditionalOnProperty(name = "slotbook.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

	private final Path root;

	public LocalStorageService(@Value("${slotbook.storage.local-dir}") String root) {
		this.root = Path.of(root).toAbsolutePath().normalize();
	}

	@Override
	public void put(String key, byte[] content, String contentType) {
		Path fajl = putanja(key);
		try {
			Files.createDirectories(fajl.getParent());
			Files.write(fajl, content);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public Optional<byte[]> get(String key) {
		Path fajl = putanja(key);
		if (!Files.exists(fajl)) {
			return Optional.empty();
		}
		try {
			return Optional.of(Files.readAllBytes(fajl));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void delete(String key) {
		try {
			Files.deleteIfExists(putanja(key));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	// Ključeve pravimo mi, ali ako ikad stigne "../" iz greške u kodu, ne smije izaći iz foldera
	private Path putanja(String key) {
		Path fajl = root.resolve(key).normalize();
		if (!fajl.startsWith(root)) {
			throw new IllegalArgumentException("Ključ izlazi iz foldera skladišta");
		}
		return fajl;
	}

}
