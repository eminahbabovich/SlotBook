package ba.slotbook.storage;

import java.net.URI;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

// Bilo koje S3-kompatibilno skladište: Cloudflare R2 na produkciji, S3Mock u testu.
// Podešavanja klijenta su po Cloudflare primjeru za Java SDK v2.
@Component
@ConditionalOnProperty(name = "slotbook.storage.type", havingValue = "s3")
public class S3StorageService implements StorageService {

	private final S3Client s3;
	private final String bucket;

	public S3StorageService(@Value("${slotbook.storage.s3.endpoint}") String endpoint,
			@Value("${slotbook.storage.s3.region}") String region,
			@Value("${slotbook.storage.s3.bucket}") String bucket,
			@Value("${slotbook.storage.s3.access-key}") String accessKey,
			@Value("${slotbook.storage.s3.secret-key}") String secretKey) {
		this.s3 = S3Client.builder()
				.endpointOverride(URI.create(endpoint))
				// R2 traži region "auto", a SDK bez regiona ne napravi klijenta
				.region(Region.of(region))
				.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
				.serviceConfiguration(S3Configuration.builder()
						// bucket u putanji (endpoint/bucket/kljuc), a ne u poddomenu, što R2 i S3Mock traže
						.pathStyleAccessEnabled(true)
						// Bez ovoga R2 odbije putObject sa 403, jer potpis ne odgovara chunked tijelu
						.chunkedEncodingEnabled(false)
						.build())
				.build();
		this.bucket = bucket;
	}

	@Override
	public void put(String key, byte[] content, String contentType) {
		s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
				RequestBody.fromBytes(content));
	}

	@Override
	public Optional<byte[]> get(String key) {
		try {
			return Optional.of(s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build())
					.asByteArray());
		} catch (NoSuchKeyException e) {
			return Optional.empty();
		}
	}

	// S3 brisanje nepostojećeg ključa ne prijavljuje kao grešku, pa ne treba posebna provjera
	@Override
	public void delete(String key) {
		s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
	}

	@PreDestroy
	void zatvori() {
		s3.close();
	}

}
