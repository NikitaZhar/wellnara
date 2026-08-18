package life.wellnara;

import life.wellnara.service.calendar.CalendarAudience;
import life.wellnara.service.calendar.CalendarLinkBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for building audience-specific appointment links.
 */
class CalendarLinkBuilderTest {

    private static final String BASE_URL = "https://app.wellnara.life";

    @Test
    @DisplayName("Provider audience links to the provider appointments page")
    void providerLink() {
        CalendarLinkBuilder builder = new CalendarLinkBuilder(BASE_URL);

        assertThat(builder.appointmentsLink(CalendarAudience.PROVIDER))
                .isEqualTo("https://app.wellnara.life/provider/appointments");
    }

    @Test
    @DisplayName("Client audience links to the client appointments page")
    void clientLink() {
        CalendarLinkBuilder builder = new CalendarLinkBuilder(BASE_URL);

        assertThat(builder.appointmentsLink(CalendarAudience.CLIENT))
                .isEqualTo("https://app.wellnara.life/client/appointments");
    }

    @Test
    @DisplayName("A trailing slash in the base URL is not duplicated")
    void trailingSlashStripped() {
        CalendarLinkBuilder builder = new CalendarLinkBuilder("https://app.wellnara.life/");

        assertThat(builder.appointmentsLink(CalendarAudience.CLIENT))
                .isEqualTo("https://app.wellnara.life/client/appointments");
    }

    @Test
    @DisplayName("The feed URL is the base URL plus the token path and .ics suffix")
    void feedUrl() {
        CalendarLinkBuilder builder = new CalendarLinkBuilder(BASE_URL);

        assertThat(builder.feedUrl("abc-123"))
                .isEqualTo("https://app.wellnara.life/calendar/abc-123.ics");
    }
}
