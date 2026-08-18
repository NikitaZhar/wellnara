package life.wellnara;

import life.wellnara.controller.CalendarFeedModelAssembler;
import life.wellnara.model.CalendarSubscription;
import life.wellnara.model.User;
import life.wellnara.service.calendar.CalendarLinkBuilder;
import life.wellnara.service.calendar.CalendarSubscriptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ui.Model;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for the profile feed-section attributes: the URL is shown only
 * while the feed is enabled, and viewing never creates a token.
 */
class CalendarFeedModelAssemblerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 9, 0);

    private final CalendarSubscriptionService subscriptionService = mock(CalendarSubscriptionService.class);
    private final CalendarLinkBuilder calendarLinkBuilder = mock(CalendarLinkBuilder.class);
    private final CalendarFeedModelAssembler assembler =
            new CalendarFeedModelAssembler(subscriptionService, calendarLinkBuilder);

    private final User user = mock(User.class);
    private final Model model = mock(Model.class);

    @Test
    @DisplayName("An enabled feed exposes enabled=true and the feed URL")
    void enabledFeedExposesUrl() {
        CalendarSubscription subscription = new CalendarSubscription(user, "tok", NOW);
        when(subscriptionService.findFor(user)).thenReturn(Optional.of(subscription));
        when(calendarLinkBuilder.feedUrl("tok")).thenReturn("https://app/calendar/tok.ics");

        assembler.populateFeed(model, user);

        verify(model).addAttribute("calendarFeedEnabled", true);
        verify(model).addAttribute("calendarFeedUrl", "https://app/calendar/tok.ics");
    }

    @Test
    @DisplayName("A disabled feed exposes enabled=false and no URL")
    void disabledFeedExposesNoUrl() {
        CalendarSubscription subscription = new CalendarSubscription(user, "tok", NOW);
        subscription.disable(NOW);
        when(subscriptionService.findFor(user)).thenReturn(Optional.of(subscription));

        assembler.populateFeed(model, user);

        verify(model).addAttribute("calendarFeedEnabled", false);
        verify(model).addAttribute(eq("calendarFeedUrl"), isNull());
    }

    @Test
    @DisplayName("No subscription exposes enabled=false and no URL")
    void absentFeedExposesNoUrl() {
        when(subscriptionService.findFor(user)).thenReturn(Optional.empty());

        assembler.populateFeed(model, user);

        verify(model).addAttribute("calendarFeedEnabled", false);
        verify(model).addAttribute(eq("calendarFeedUrl"), isNull());
    }
}
