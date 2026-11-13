package ba.slotbook.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.net.URI;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

// S3Mock je zamjena za R2 u testu: isti S3 API, bez naloga. MinIO više nema javnih slika.
// Dokazuje da naš klijent radi s S3 API-jem, ali ne i s R2 posebno (to se vidi tek na deployu).
@Testcontainers
class S3StorageServiceTest {

	private static final String BUCKET = "slotbook-slike";

	@Container
	static final GenericContainer<?> S3MOCK = new GenericContainer<>(DockerImageName.parse("adobe/s3mock:5.2.3"))
			.withExposedPorts(9090);

	private static String endpoint() {
		return "http://" + S3MOCK.getHost() + ":" + S3MOCK.getMappedPort(9090);
	}

	// Bucket na R2 pravim ja u dashboardu, a aplikacija ga ne pravi, pa ga ni test ne traži od nje
	@BeforeAll
	static void bucket() {
		try (S3Client klijent = S3Client.builder()
				.endpointOverride(URI.create(endpoint()))
				.region(Region.of("auto"))
				.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
				.forcePathStyle(true)
				.build()) {
			klijent.createBucket(b -> b.bucket(BUCKET));
		}
	}

	private S3StorageService skladiste() {
		return new S3StorageService(endpoint(), "auto", BUCKET, "test", "test");
	}

	@Test
	void upisanoSeCitaIstoIBriseSe() {
		var skladiste = skladiste();
		byte[] sadrzaj = { (byte) 0x89, 'P', 'N', 'G', 1, 2, 3 };

		skladiste.put("resources/sala/slika.png", sadrzaj, "image/png");

		assertThat(skladiste.get("resources/sala/slika.png")).hasValueSatisfying(b -> assertThat(b).isEqualTo(sadrzaj));
		skladiste.delete("resources/sala/slika.png");
		assertThat(skladiste.get("resources/sala/slika.png")).isEmpty();
	}

	@Test
	void nepostojeciKljucNijeGreska() {
		var skladiste = skladiste();

		assertThat(skladiste.get("resources/nema.png")).isEmpty();
		assertThatCode(() -> skladiste.delete("resources/nema.png")).doesNotThrowAnyException();
	}

}
