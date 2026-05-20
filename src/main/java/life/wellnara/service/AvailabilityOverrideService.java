package life.wellnara.service;

import life.wellnara.model.AvailabilityOverride;
import life.wellnara.model.AvailabilityOverrideType;
import life.wellnara.model.User;
import life.wellnara.repository.AvailabilityOverrideRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Service for one-time provider availability overrides.
 */
@Service
public class AvailabilityOverrideService {

    private final AvailabilityOverrideRepository availabilityOverrideRepository;
    private final ProviderCalendarValidator calendarValidator;

    /**
     * Creates availability override service.
     *
     * @param availabilityOverrideRepository repository for availability overrides
     * @param calendarValidator validator for override input
     */
    public AvailabilityOverrideService(AvailabilityOverrideRepository availabilityOverrideRepository,
                                       ProviderCalendarValidator calendarValidator) {
        this.availabilityOverrideRepository = availabilityOverrideRepository;
        this.calendarValidator = calendarValidator;
    }

    /**
     * Creates one-time provider availability override.
     *
     * @param provider provider who owns the override
     * @param date override date
     * @param startTime override start time
     * @param endTime override end time
     * @param type override type
     */
    @Transactional
    public void createAvailabilityOverride(User provider,
                                           LocalDate date,
                                           LocalTime startTime,
                                           LocalTime endTime,
                                           AvailabilityOverrideType type) {
        calendarValidator.validateAvailabilityOverride(provider, date, startTime, endTime, type);

        availabilityOverrideRepository.save(
                new AvailabilityOverride(
                        provider,
                        date,
                        startTime,
                        endTime,
                        type
                )
        );
    }

    /**
     * Deletes provider availability override.
     *
     * @param provider provider who owns the override
     * @param overrideId override identifier
     */
    @Transactional
    public void deleteAvailabilityOverride(User provider, Long overrideId) {
        AvailabilityOverride override = availabilityOverrideRepository.findById(overrideId)
                .orElseThrow(() -> new IllegalArgumentException("Availability override not found"));

        if (!override.getProvider().getId().equals(provider.getId())) {
            throw new IllegalArgumentException("Availability override does not belong to provider");
        }

        availabilityOverrideRepository.delete(override);
    }

    /**
     * Returns provider availability overrides.
     *
     * @param provider provider who owns the overrides
     * @return ordered provider availability overrides
     */
    @Transactional(readOnly = true)
    public List<AvailabilityOverride> getAvailabilityOverrides(User provider) {
        return availabilityOverrideRepository
                .findAllByProviderOrderByOverrideDateAscStartTimeAsc(provider);
    }
}