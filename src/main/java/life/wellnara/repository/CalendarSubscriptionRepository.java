package life.wellnara.repository;

import life.wellnara.model.CalendarSubscription;
import life.wellnara.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for personal calendar feed subscriptions.
 */
public interface CalendarSubscriptionRepository extends JpaRepository<CalendarSubscription, Long> {

    /**
     * Finds the subscription owned by the given user.
     *
     * @param user subscription owner
     * @return found subscription or empty result
     */
    Optional<CalendarSubscription> findByUser(User user);

    /**
     * Finds the subscription with the given feed token.
     *
     * @param token feed token
     * @return found subscription or empty result
     */
    Optional<CalendarSubscription> findByToken(String token);
}
