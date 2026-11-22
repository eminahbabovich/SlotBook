package ba.slotbook.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import ba.slotbook.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class HealthApiTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	HealthEndpointGroups grupe;

	@Autowired
	ApplicationContext context;

	@Test
	void livenessIReadinessSuOtvoreniBezPrijave() throws Exception {
		mockMvc.perform(get("/actuator/health/liveness"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
		mockMvc.perform(get("/actuator/health/readiness"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void readinessGledaBazuALivenessNe() {
		assertThat(grupe.get("readiness").isMember("db")).isTrue();
		assertThat(grupe.get("liveness").isMember("db")).isFalse();
	}

	@Test
	void readinessVraca503KadAplikacijaNePrimaZahtjeve() throws Exception {
		AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC);
		try {
			mockMvc.perform(get("/actuator/health/readiness"))
					.andExpect(status().isServiceUnavailable());
			mockMvc.perform(get("/actuator/health/liveness"))
					.andExpect(status().isOk());
		} finally {
			AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
		}
	}

}
