package ba.slotbook.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

import ba.slotbook.TestcontainersConfiguration;

// @Scheduled bez @EnableScheduling ne radi ništa i ne javlja grešku, pa se provjerava da je posao
// stvarno prijavljen rasporedu, i to svake minute
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BookingJobTest {

	@Autowired
	ScheduledTaskHolder raspored;

	@Test
	void posaoZaRezervacijeSeVrtiSvakeMinute() {
		assertThat(raspored.getScheduledTasks())
				.filteredOn(zadatak -> zadatak.toString().contains("BookingJob.obradi"))
				.singleElement()
				.satisfies(zadatak -> assertThat(((FixedDelayTask) zadatak.getTask()).getIntervalDuration())
						.isEqualTo(Duration.ofMinutes(1)));
	}

}
