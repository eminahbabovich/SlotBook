package ba.slotbook;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
	}

	@Bean
	DynamicPropertyRegistrar testnaPodesavanja() {
		return registry -> {
			registry.add("slotbook.jwt.secret", () -> "testni-kljuc-samo-za-testove-najmanje-32-bajta");
			// Slike iz testova idu u target, da ih mvn clean obriše i da ne završe u uploads/
			registry.add("slotbook.storage.local-dir", () -> "target/test-uploads");
		};
	}

}
