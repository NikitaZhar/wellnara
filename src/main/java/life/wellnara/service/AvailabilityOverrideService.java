package life.wellnara.service;

import life.wellnara.dto.AvailabilityOverrideForm;
import life.wellnara.model.AvailabilityOverride;
import life.wellnara.model.AvailabilityOverrideType;
import life.wellnara.model.User;
import life.wellnara.repository.AvailabilityOverrideRepository;
import life.wellnara.service.time.ApplicationTimeService;

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
	private final ApplicationTimeService applicationTimeService;

	/**
	 * Creates availability override service.
	 *
	 * @param availabilityOverrideRepository repository for availability overrides
	 * @param calendarValidator validator for override input
	 */
	public AvailabilityOverrideService(AvailabilityOverrideRepository availabilityOverrideRepository,
			ProviderCalendarValidator calendarValidator,
			ApplicationTimeService applicationTimeService) {
		this.availabilityOverrideRepository = availabilityOverrideRepository;
		this.calendarValidator = calendarValidator;
		this.applicationTimeService = applicationTimeService;
	}

	/**
	 * Creates availability override service.
	 *
	 * @param availabilityOverrideRepository repository for availability overrides
	 * @param calendarValidator validator for override input
	 * @param applicationTimeService service for application time calculations
	 */
	@Transactional
	public void createAvailabilityOverride(User provider,
	                                       LocalDate date,
	                                       LocalTime startTime,
	                                       LocalTime endTime,
	                                       AvailabilityOverrideType type) {
	    calendarValidator.validateAvailabilityOverride(
	            provider,
	            date,
	            startTime,
	            endTime,
	            type,
	            applicationTimeService.currentProviderCalendarDate(provider)
	    );

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
	 * Deletes provider availability overrides whose end date and time have already passed.
	 *
	 * @param provider provider who owns the overrides
	 */
	@Transactional
	public void deleteExpiredAvailabilityOverrides(User provider) {
	    LocalDate today = applicationTimeService.currentProviderCalendarDate(provider);
	    LocalTime now = applicationTimeService.currentProviderCalendarTime(provider);

	    availabilityOverrideRepository.deleteAllByProviderAndOverrideDateBefore(provider, today);
	    availabilityOverrideRepository.deleteAllByProviderAndOverrideDateAndEndTimeBefore(provider, today, now);
	}

	/**
	 * Returns provider availability overrides after removing expired records.
	 *
	 * @param provider provider who owns the overrides
	 * @return ordered provider availability overrides
	 */
	@Transactional
	public List<AvailabilityOverride> getAvailabilityOverrides(User provider) {
		deleteExpiredAvailabilityOverrides(provider);

		return availabilityOverrideRepository
				.findAllByProviderOrderByOverrideDateAscStartTimeAsc(provider);
	}

	/**
	 * Replaces all provider one-time changes with the given list inside the
	 * caller transaction, so saving the whole calendar stays atomic.
	 * Items are already validated by the caller.
	 *
	 * @param provider provider who owns the overrides
	 * @param items one-time changes to persist
	 */
	@Transactional
	public void replaceOverrides(User provider, List<AvailabilityOverrideForm> items) {
		availabilityOverrideRepository.deleteAllByProvider(provider);

		if (items == null) {
			return;
		}

		for (AvailabilityOverrideForm item : items) {
			if (item == null || item.getType() == null) {
				continue;
			}

			availabilityOverrideRepository.save(new AvailabilityOverride(
					provider, item.getDate(), item.getStartTime(), item.getEndTime(), item.getType()));
		}
	}
}
