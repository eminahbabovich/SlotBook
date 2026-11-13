package ba.slotbook.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalStorageServiceTest {

	@TempDir
	Path folder;

	@Test
	void upisanoSeCitaIstoIBriseSe() {
		var skladiste = new LocalStorageService(folder.toString());
		byte[] sadrzaj = { 1, 2, 3 };

		skladiste.put("resources/sala/slika.png", sadrzaj, "image/png");

		assertThat(skladiste.get("resources/sala/slika.png")).hasValueSatisfying(b -> assertThat(b).isEqualTo(sadrzaj));
		skladiste.delete("resources/sala/slika.png");
		assertThat(skladiste.get("resources/sala/slika.png")).isEmpty();
	}

	@Test
	void nepostojeciKljucNijeGreska() {
		var skladiste = new LocalStorageService(folder.toString());

		assertThat(skladiste.get("resources/nema.png")).isEmpty();
		assertThatCode(() -> skladiste.delete("resources/nema.png")).doesNotThrowAnyException();
	}

	// Ključ s ../ bi inače pisao izvan foldera, npr. preko application.properties
	@Test
	void kljucNeMozeIzacIzFolderaSkladista() throws Exception {
		Path skladisteFolder = Files.createDirectory(folder.resolve("uploads"));
		var skladiste = new LocalStorageService(skladisteFolder.toString());

		assertThatThrownBy(() -> skladiste.put("../izvan.txt", new byte[] { 1 }, "text/plain"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(folder.resolve("izvan.txt")).doesNotExist();
	}

}
