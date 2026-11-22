package ba.slotbook.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

import ba.slotbook.TestcontainersConfiguration;
import io.sentry.IScopes;

// Bez SENTRY_DSN (lokalno, testovi, CI) Sentry se ne podiže, pa nema ni pokušaja slanja
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SentryBezDsnaTest {

	@Autowired
	ApplicationContext context;

	@Test
	void bezDsnaSentryNijeUkljucen() {
		assertThat(context.getBeanNamesForType(IScopes.class)).isEmpty();
	}

}
