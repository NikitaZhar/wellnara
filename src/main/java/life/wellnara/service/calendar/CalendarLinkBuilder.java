package life.wellnara.service.calendar;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Builds absolute application links embedded in calendar event descriptions.
 *
 * <p>Because a mirrored event is read-only in the external calendar, every
 * action (reschedule, cancel) happens back in the application. The link points
 * the recipient to the appointments page for their own role.
 */
@Component
public class CalendarLinkBuilder {

    private final String publicBaseUrl;

    /**
     * Creates the calendar link builder.
     *
     * @param publicBaseUrl public application base URL used to build absolute links
     */
    public CalendarLinkBuilder(@Value("${wellnara.public-base-url}") String publicBaseUrl) {
        this.publicBaseUrl = stripTrailingSlash(publicBaseUrl);
    }

    /**
     * Builds the absolute appointments-page link for the given audience.
     *
     * @param audience participant the link is intended for
     * @return absolute URL of that participant's appointments page
     */
    public String appointmentsLink(CalendarAudience audience) {
        Objects.requireNonNull(audience, "audience is required");

        return publicBaseUrl + switch (audience) {
            case PROVIDER -> "/provider/appointments";
            case CLIENT -> "/client/appointments";
        };
    }

    /**
     * Builds the absolute ICS feed URL for the given subscription token.
     *
     * @param token feed token
     * @return absolute {@code .ics} feed URL
     */
    public String feedUrl(String token) {
        Objects.requireNonNull(token, "token is required");

        return publicBaseUrl + "/calendar/" + token + ".ics";
    }

    private static String stripTrailingSlash(String url) {
        Objects.requireNonNull(url, "Public base URL is required");

        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
