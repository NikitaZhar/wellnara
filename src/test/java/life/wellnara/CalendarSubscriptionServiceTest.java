package life.wellnara;

import life.wellnara.model.CalendarSubscription;
import life.wellnara.model.User;
import life.wellnara.repository.CalendarSubscriptionRepository;
import life.wellnara.service.calendar.CalendarFeedTokenGenerator;
import life.wellnara.service.calendar.CalendarSubscriptionService;
import life.wellnara.service.time.ApplicationTimeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for the subscription lifecycle: create-on-first-use,
 * regenerate, disable and active-token resolution.
 */
class CalendarSubscriptionServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 9, 0);

    private final CalendarSubscriptionRepository subscriptionRepository =
            mock(CalendarSubscriptionRepository.class);
    private final CalendarFeedTokenGenerator tokenGenerator = mock(CalendarFeedTokenGenerator.class);
    private final ApplicationTimeService applicationTimeService = mock(ApplicationTimeService.class);
    private final CalendarSubscriptionService service = new CalendarSubscriptionService(
            subscriptionRepository, tokenGenerator, applicationTimeService);

    private final User user = mock(User.class);

    @Test
    @DisplayName("getOrCreate creates an enabled subscription on first use")
    void getOrCreateCreates() {
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.empty());
        when(tokenGenerator.generate()).thenReturn("fresh-token");
        when(applicationTimeService.currentUtcDateTime()).thenReturn(NOW);
        when(subscriptionRepository.save(any(CalendarSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CalendarSubscription subscription = service.getOrCreate(user);

        assertThat(subscription.getToken()).isEqualTo("fresh-token");
        assertThat(subscription.isEnabled()).isTrue();
        verify(subscriptionRepository).save(any(CalendarSubscription.class));
    }

    @Test
    @DisplayName("getOrCreate returns the existing subscription without saving")
    void getOrCreateReturnsExisting() {
        CalendarSubscription existing = new CalendarSubscription(user, "old-token", NOW);
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(existing));

        assertThat(service.getOrCreate(user)).isSameAs(existing);
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("regenerate issues a new token and keeps the feed enabled")
    void regenerateIssuesNewToken() {
        CalendarSubscription existing = new CalendarSubscription(user, "old-token", NOW);
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(existing));
        when(tokenGenerator.generate()).thenReturn("new-token");

        CalendarSubscription subscription = service.regenerate(user);

        assertThat(subscription.getToken()).isEqualTo("new-token");
        assertThat(subscription.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("disable turns off an existing subscription")
    void disableTurnsOff() {
        CalendarSubscription existing = new CalendarSubscription(user, "old-token", NOW);
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.of(existing));
        when(applicationTimeService.currentUtcDateTime()).thenReturn(NOW);

        service.disable(user);

        assertThat(existing.isEnabled()).isFalse();
        assertThat(existing.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("disable is a no-op when there is no subscription")
    void disableNoOpWhenAbsent() {
        when(subscriptionRepository.findByUser(user)).thenReturn(Optional.empty());

        service.disable(user);
    }

    @Test
    @DisplayName("findActiveOwner resolves an enabled token to its owner")
    void findActiveOwnerResolvesEnabled() {
        CalendarSubscription enabled = new CalendarSubscription(user, "tok", NOW);
        when(subscriptionRepository.findByToken("tok")).thenReturn(Optional.of(enabled));

        assertThat(service.findActiveOwner("tok")).contains(user);
    }

    @Test
    @DisplayName("findActiveOwner returns empty for a disabled token")
    void findActiveOwnerEmptyWhenDisabled() {
        CalendarSubscription disabled = new CalendarSubscription(user, "tok", NOW);
        disabled.disable(NOW);
        when(subscriptionRepository.findByToken("tok")).thenReturn(Optional.of(disabled));

        assertThat(service.findActiveOwner("tok")).isEmpty();
    }

    @Test
    @DisplayName("findActiveOwner returns empty for an unknown or blank token")
    void findActiveOwnerEmptyWhenUnknownOrBlank() {
        when(subscriptionRepository.findByToken("missing")).thenReturn(Optional.empty());

        assertThat(service.findActiveOwner("missing")).isEmpty();
        assertThat(service.findActiveOwner("  ")).isEmpty();
        assertThat(service.findActiveOwner(null)).isEmpty();
    }
}
