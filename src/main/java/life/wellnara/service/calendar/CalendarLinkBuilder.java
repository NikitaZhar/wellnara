package life.wellnara.service.calendar;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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

    private static final DateTimeFormatter BASIC_UTC =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

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
     * Builds a one-click "add to Google Calendar" link for a single event.
     *
     * <p>Opens Google Calendar's pre-filled new-event window. All event data is
     * carried in the URL, so this works even when the server is not reachable by
     * Google (unlike the subscription feed).
     *
     * @param event event to add
     * @return absolute Google Calendar create-event link
     */
    public String googleTemplateUrl(CalendarEvent event) {
        Objects.requireNonNull(event, "event is required");

        String dates = BASIC_UTC.format(event.start()) + "/" + BASIC_UTC.format(event.end());

        return "https://calendar.google.com/calendar/render?action=TEMPLATE"
                + "&text=" + encode(event.summary())
                + "&dates=" + dates
                + "&details=" + encode(event.description());
    }

    /**
     * Builds a one-click "add to Outlook Calendar" link for a single event.
     *
     * <p>Opens Outlook on the web with the event fields pre-filled. Uses the
     * personal (outlook.com) compose endpoint; work/school accounts on
     * outlook.office.com follow the same path if the user swaps the host.
     *
     * @param event event to add
     * @return absolute Outlook Calendar compose link
     */
    public String outlookComposeUrl(CalendarEvent event) {
        Objects.requireNonNull(event, "event is required");

        return "https://outlook.live.com/calendar/0/deeplink/compose"
                + "?path=/calendar/action/compose&rru=addevent"
                + "&subject=" + encode(event.summary())
                + "&startdt=" + DateTimeFormatter.ISO_INSTANT.format(event.start())
                + "&enddt=" + DateTimeFormatter.ISO_INSTANT.format(event.end())
                + "&body=" + encode(event.description());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String stripTrailingSlash(String url) {
        Objects.requireNonNull(url, "Public base URL is required");

        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
