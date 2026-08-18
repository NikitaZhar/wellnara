package life.wellnara.controller;

import life.wellnara.service.calendar.CalendarFeedService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Serves the public, token-addressed personal calendar feed.
 *
 * <p>The path is deliberately outside the authenticated areas: the opaque token
 * is the only credential, so calendar clients (Google, Apple, Outlook) can poll
 * it without a login. An unknown or disabled token yields {@code 404}, revealing
 * nothing about whether it ever existed.
 */
@Controller
public class CalendarFeedController {

    private static final MediaType CALENDAR_MEDIA_TYPE =
            MediaType.parseMediaType("text/calendar; charset=UTF-8");

    private final CalendarFeedService calendarFeedService;

    /**
     * Creates the calendar feed controller.
     *
     * @param calendarFeedService service that builds the feed for a token
     */
    public CalendarFeedController(CalendarFeedService calendarFeedService) {
        this.calendarFeedService = calendarFeedService;
    }

    /**
     * Returns the ICS feed for the given token.
     *
     * @param token feed token
     * @return the {@code text/calendar} feed, or {@code 404} if the token is
     *         unknown or the feed is disabled
     */
    @GetMapping("/calendar/{token}.ics")
    public ResponseEntity<String> feed(@PathVariable String token) {
        return calendarFeedService.feedFor(token)
                .map(feed -> ResponseEntity.ok().contentType(CALENDAR_MEDIA_TYPE).body(feed))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
