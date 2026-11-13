package ba.slotbook.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

// Mali Spring kontekst sa samo dvije implementacije, bez baze i bez weba: provjerava se
// samo koju od njih Spring izabere po slotbook.storage.type
class StorageSelectionTest {

	private final ApplicationContextRunner kontekst = new ApplicationContextRunner()
			// Kao u aplikaciji. Bez ovoga Spring nepoznat placeholder ostavi kao običan tekst.
			.withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
			.withUserConfiguration(LocalStorageService.class, S3StorageService.class)
			.withPropertyValues("slotbook.storage.local-dir=target/test-uploads");

	@Test
	void bezPodesavanjaSlikeIduNaDisk() {
		kontekst.run(c -> assertThat(c).hasSingleBean(StorageService.class).hasSingleBean(LocalStorageService.class));
	}

	@Test
	void s3JeIzabranKadJeTipS3() {
		kontekst.withPropertyValues("slotbook.storage.type=s3", "slotbook.storage.s3.endpoint=http://localhost:9090",
				"slotbook.storage.s3.region=auto", "slotbook.storage.s3.bucket=slike",
				"slotbook.storage.s3.access-key=test", "slotbook.storage.s3.secret-key=test")
				.run(c -> assertThat(c).hasSingleBean(StorageService.class).hasSingleBean(S3StorageService.class));
	}

	// Bolje da aplikacija ne starta nego da starta i padne na prvom uploadu
	@Test
	void s3BezEndpointaNeStarta() {
		kontekst.withPropertyValues("slotbook.storage.type=s3")
				.run(c -> assertThat(c).getFailure().rootCause()
						.hasMessageContaining("Could not resolve placeholder 'slotbook.storage.s3.endpoint'"));
	}

}
