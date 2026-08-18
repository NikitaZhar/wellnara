package life.wellnara.service.calendar;

import life.wellnara.model.CalendarSubscription;
import life.wellnara.model.User;
import life.wellnara.repository.CalendarSubscriptionRepository;
import life.wellnara.service.time.ApplicationTimeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

/**
 * Manages the lifecycle of a user's personal calendar feed subscription:
 * obtaining (creating on first use), regenerating and disabling the token, and
 * resolving an active token back to its owner for the feed endpoint.
 */
@Service
public class CalendarSubscriptionService {

    private final CalendarSubscriptionRepository subscriptionRepository;
    private final CalendarFeedTokenGenerator tokenGenerator;
    private final ApplicationTimeService applicationTimeService;

    /**
     * Creates the calendar subscription service.
     *
     * @param subscriptionRepository repository for subscriptions
     * @param tokenGenerator         generator of opaque feed tokens
     * @param applicationTimeService source of creation and revocation timestamps
     */
    public CalendarSubscriptionService(CalendarSubscriptionRepository subscriptionRepository,
                                       CalendarFeedTokenGenerator tokenGenerator,
                                       ApplicationTimeService applicationTimeService) {
        this.subscriptionRepository = subscriptionRepository;
        this.tokenGenerator = tokenGenerator;
        this.applicationTimeService = applicationTimeService;
    }

    /**
     * Returns the user's subscription, creating an enabled one on first use.
     *
     * @param user subscription owner
     * @return the user's subscription
     */
    @Transactional
    public CalendarSubscription getOrCreate(User user) {
        Objects.requireNonNull(user, "user is required");

        return subscriptionRepository.findByUser(user).orElseGet(() -> create(user));
    }

    /**
     * Issues a fresh token for the user, creating the subscription if needed. The
     * previous feed URL stops working.
     *
     * @param user subscription owner
     * @return the updated subscription
     */
    @Transactional
    public CalendarSubscription regenerate(User user) {
        CalendarSubscription subscription = getOrCreate(user);
        subscription.regenerate(tokenGenerator.generate());

        return subscription;
    }

    /**
     * Disables the user's feed, if one exists. The feed URL stops resolving until
     * the subscription is regenerated.
     *
     * @param user subscription owner
     */
    @Transactional
    public void disable(User user) {
        Objects.requireNonNull(user, "user is required");

        subscriptionRepository.findByUser(user)
                .ifPresent(subscription -> subscription.disable(applicationTimeService.currentUtcDateTime()));
    }

    /**
     * Returns the user's subscription without creating one, for display.
     *
     * @param user subscription owner
     * @return the subscription if it exists, otherwise empty
     */
    @Transactional(readOnly = true)
    public Optional<CalendarSubscription> findFor(User user) {
        Objects.requireNonNull(user, "user is required");

        return subscriptionRepository.findByUser(user);
    }

    /**
     * Resolves an active feed token to its owner.
     *
     * @param token feed token
     * @return the owner if the token exists and the feed is enabled, otherwise empty
     */
    @Transactional(readOnly = true)
    public Optional<User> findActiveOwner(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        return subscriptionRepository.findByToken(token)
                .filter(CalendarSubscription::isEnabled)
                .map(CalendarSubscription::getUser);
    }

    private CalendarSubscription create(User user) {
        return subscriptionRepository.save(new CalendarSubscription(
                user, tokenGenerator.generate(), applicationTimeService.currentUtcDateTime()));
    }
}
