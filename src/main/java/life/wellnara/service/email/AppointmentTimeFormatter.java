package life.wellnara.service.email;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * Formats an appointment start for the human-readable body of a notification.
 *
 * <p>The instant is rendered in the provider's calendar time zone with an
 * explicit zone label (e.g. {@code Mon, 17 Aug 2026 15:00 CEST}). Using the
 * provider zone keeps the displayed time unambiguous for both participants even
 * if either has since moved to another zone, and matches the times shown in the
 * application UI.
 */
@Component
public class AppointmentTimeFormatter {

    private static final String PATTERN = "EEE, d MMM yyyy HH:mm z";

    /**
     * Formats the given instant in the given zone with an explicit zone label.
     *
     * @param instant appointment start instant
     * @param zone    provider calendar time zone
     * @param locale  language for the month and day names
     * @return formatted date-time, e.g. {@code Mon, 17 Aug 2026 15:00 CEST}
     */
    public String format(Instant instant, ZoneId zone, Locale locale) {
        Objects.requireNonNull(instant, "instant is required");
        Objects.requireNonNull(zone, "zone is required");
        Objects.requireNonNull(locale, "locale is required");

        return DateTimeFormatter.ofPattern(PATTERN, locale).format(instant.atZone(zone));
    }
}
