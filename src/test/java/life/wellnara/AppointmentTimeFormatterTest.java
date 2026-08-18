package life.wellnara;

import life.wellnara.service.email.AppointmentTimeFormatter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for rendering the appointment start in the provider's zone
 * with an explicit zone label.
 */
class AppointmentTimeFormatterTest {

    private final AppointmentTimeFormatter formatter = new AppointmentTimeFormatter();

    @Test
    @DisplayName("Summer instant renders in the provider zone with the daylight label")
    void rendersSummerZoneLabel() {
        String formatted = formatter.format(
                Instant.parse("2026-08-17T13:00:00Z"), ZoneId.of("Europe/Bratislava"));

        assertThat(formatted).isEqualTo("Mon, 17 Aug 2026 15:00 CEST");
    }

    @Test
    @DisplayName("Winter instant renders with the standard-time label and shifted hour")
    void rendersWinterZoneLabel() {
        String formatted = formatter.format(
                Instant.parse("2026-01-17T13:00:00Z"), ZoneId.of("Europe/Bratislava"));

        assertThat(formatted).isEqualTo("Sat, 17 Jan 2026 14:00 CET");
    }

    @Test
    @DisplayName("A different zone shifts the hour and label accordingly")
    void rendersOtherZone() {
        String formatted = formatter.format(
                Instant.parse("2026-08-17T13:00:00Z"), ZoneId.of("America/New_York"));

        assertThat(formatted).isEqualTo("Mon, 17 Aug 2026 09:00 EDT");
    }
}
