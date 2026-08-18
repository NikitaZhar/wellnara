package life.wellnara;

import life.wellnara.service.calendar.CalendarFeedTokenGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for the feed token: URL-safe alphabet and non-repeating output.
 */
class CalendarFeedTokenGeneratorTest {

    private final CalendarFeedTokenGenerator generator = new CalendarFeedTokenGenerator();

    @Test
    @DisplayName("A token is URL-safe and long enough to be unguessable")
    void tokenIsUrlSafeAndLong() {
        String token = generator.generate();

        assertThat(token).matches("^[A-Za-z0-9_-]+$");
        assertThat(token.length()).isGreaterThanOrEqualTo(43);
    }

    @Test
    @DisplayName("Two tokens differ")
    void tokensDiffer() {
        assertThat(generator.generate()).isNotEqualTo(generator.generate());
    }
}
