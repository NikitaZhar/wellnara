package life.wellnara.repository;

import life.wellnara.model.AvailabilityOverride;
import life.wellnara.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Repository for one-time provider availability overrides.
 */
public interface AvailabilityOverrideRepository extends JpaRepository<AvailabilityOverride, Long> {

    /**
     * Returns all availability overrides for the specified provider ordered by date and start time.
     *
     * @param provider provider who owns the overrides
     * @return ordered provider availability overrides
     */
    List<AvailabilityOverride> findAllByProviderOrderByOverrideDateAscStartTimeAsc(User provider);

    /**
     * Deletes provider availability overrides that ended before the specified date.
     *
     * @param provider provider who owns the overrides
     * @param date current provider-local date
     */
    void deleteAllByProviderAndOverrideDateBefore(User provider, LocalDate date);

    /**
     * Deletes provider availability overrides from the specified date whose end time has already passed.
     *
     * @param provider provider who owns the overrides
     * @param date current provider-local date
     * @param time current provider-local time
     */
    void deleteAllByProviderAndOverrideDateAndEndTimeBefore(User provider, LocalDate date, LocalTime time);
}