package ba.slotbook.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Bez ovoga Spring ignoriše svaki @Scheduled, bez greške i bez upozorenja u logu
@Configuration
@EnableScheduling
class SchedulingConfig {
}
