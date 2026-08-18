package life.wellnara.controller;

import life.wellnara.model.CalendarSubscription;
import life.wellnara.model.User;
import life.wellnara.service.calendar.CalendarLinkBuilder;
import life.wellnara.service.calendar.CalendarSubscriptionService;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.util.Optional;

/**
 * Adds the calendar feed section attributes to a profile page, for both roles.
 *
 * <p>Exposes whether the feed is enabled and, when it is, its absolute ICS URL.
 * Reading only (no token is created just by viewing the profile): the URL
 * appears once the user enables the feed.
 */
@Component
public class CalendarFeedModelAssembler {

    private final CalendarSubscriptionService subscriptionService;
    private final CalendarLinkBuilder calendarLinkBuilder;

    /**
     * Creates the calendar feed model assembler.
     *
     * @param subscriptionService service for reading the user's subscription
     * @param calendarLinkBuilder builder of the feed URL
     */
    public CalendarFeedModelAssembler(CalendarSubscriptionService subscriptionService,
                                      CalendarLinkBuilder calendarLinkBuilder) {
        this.subscriptionService = subscriptionService;
        this.calendarLinkBuilder = calendarLinkBuilder;
    }

    /**
     * Populates the feed attributes for the given user onto the model.
     *
     * @param model model to populate
     * @param user  profile owner
     */
    public void populateFeed(Model model, User user) {
        Optional<CalendarSubscription> activeSubscription = subscriptionService.findFor(user)
                .filter(CalendarSubscription::isEnabled);
        Optional<String> activeToken = activeSubscription.map(CalendarSubscription::getToken);

        model.addAttribute("calendarFeedEnabled", activeSubscription.isPresent());
        model.addAttribute("calendarFeedUrl", activeToken
                .map(calendarLinkBuilder::feedUrl)
                .orElse(null));
        model.addAttribute("calendarFeedWebcalUrl", activeToken
                .map(calendarLinkBuilder::webcalFeedUrl)
                .orElse(null));
        model.addAttribute("calendarFeedGoogleUrl", activeToken
                .map(calendarLinkBuilder::googleSubscribeUrl)
                .orElse(null));
    }
}
