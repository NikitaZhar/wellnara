package life.wellnara.service.time;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Application-wide clock configuration.
 */
@Configuration
public class ApplicationClockConfig {

    /**
     * Provides a UTC application clock independent from server timezone.
     *
     * @return UTC application clock
     */
    @Bean
    public Clock applicationClock() {
        return Clock.systemUTC();
    }
}