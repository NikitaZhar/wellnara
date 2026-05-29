package life.wellnara.service.time;

import life.wellnara.model.AvailabilityPeriod;
import life.wellnara.model.User;
import life.wellnara.repository.AvailabilityPeriodRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Central application service for resolving current time in business time zones.
 */
@Service
public class ApplicationTimeService {

    private static final ZoneId DEFAULT_ZONE = ZoneOffset.UTC;

    private final Clock clock;
    private final AvailabilityPeriodRepository availabilityPeriodRepository;

    /**
     * Creates application time service.
     *
     * @param clock application clock
     * @param availabilityPeriodRepository repository for provider availability periods
     */
    public ApplicationTimeService(Clock clock, AvailabilityPeriodRepository availabilityPeriodRepository) {
        this.clock = clock;
        this.availabilityPeriodRepository = availabilityPeriodRepository;
    }

    /**
     * Returns current UTC date-time.
     *
     * @return current UTC date-time
     */
    public LocalDateTime currentUtcDateTime() {
        return LocalDateTime.now(clock);
    }

    /**
     * Returns current date in the specified timezone.
     *
     * @param zoneId target timezone
     * @return current local date
     */
    public LocalDate currentDate(ZoneId zoneId) {
        return LocalDate.now(clock.withZone(zoneId));
    }

    /**
     * Returns current time in the specified timezone.
     *
     * @param zoneId target timezone
     * @return current local time
     */
    public LocalTime currentTime(ZoneId zoneId) {
        return LocalTime.now(clock.withZone(zoneId));
    }

    /**
     * Resolves provider calendar timezone from the latest saved availability period.
     *
     * @param provider provider who owns the calendar
     * @return provider calendar timezone
     */
    @Transactional(readOnly = true)
    public ZoneId resolveProviderCalendarZone(User provider) {
        if (provider == null) {
            return DEFAULT_ZONE;
        }

        return availabilityPeriodRepository
                .findTopByProviderOrderByCreatedAtDesc(provider)
                .map(AvailabilityPeriod::getProviderTimezone)
                .map(this::toZoneIdOrDefault)
                .orElse(DEFAULT_ZONE);
    }

    /**
     * Returns current provider calendar date.
     *
     * @param provider provider who owns the calendar
     * @return current date in provider calendar timezone
     */
    @Transactional(readOnly = true)
    public LocalDate currentProviderCalendarDate(User provider) {
        return currentDate(resolveProviderCalendarZone(provider));
    }

    /**
     * Returns current provider calendar time.
     *
     * @param provider provider who owns the calendar
     * @return current time in provider calendar timezone
     */
    @Transactional(readOnly = true)
    public LocalTime currentProviderCalendarTime(User provider) {
        return currentTime(resolveProviderCalendarZone(provider));
    }
    
    public LocalDateTime currentDateTime(ZoneId zoneId) {
        return LocalDateTime.now(clock.withZone(zoneId));
    }

    private ZoneId toZoneIdOrDefault(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException exception) {
            return DEFAULT_ZONE;
        }
    }
}