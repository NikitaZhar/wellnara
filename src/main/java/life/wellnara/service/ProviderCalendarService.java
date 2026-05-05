package life.wellnara.service;

import life.wellnara.dto.CalendarTerm;
import life.wellnara.dto.ProviderCalendarForm;
import life.wellnara.exception.CalendarValidationException;
import life.wellnara.model.AvailabilityDay;
import life.wellnara.model.AvailabilityPeriod;
import life.wellnara.model.AvailabilityRule;
import life.wellnara.model.User;
import life.wellnara.repository.AvailabilityPeriodRepository;
import life.wellnara.repository.AvailabilityRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for provider calendar availability management.
 */
@Service
public class ProviderCalendarService {

	private final AvailabilityPeriodRepository availabilityPeriodRepository;
	private final AvailabilityRuleRepository availabilityRuleRepository;

	/**
	 * Creates provider calendar service.
	 *
	 * @param availabilityPeriodRepository repository for availability periods
	 * @param availabilityRuleRepository repository for availability rules
	 */
	public ProviderCalendarService(AvailabilityPeriodRepository availabilityPeriodRepository,
			AvailabilityRuleRepository availabilityRuleRepository) {
		this.availabilityPeriodRepository = availabilityPeriodRepository;
		this.availabilityRuleRepository = availabilityRuleRepository;
	}

	/**
	 * Saves provider calendar availability from form input.
	 *
	 * @param provider provider who owns the calendar
	 * @param form calendar form input
	 */
	@Transactional
	public void saveCalendar(User provider, ProviderCalendarForm form) {
		validateForm(form);

		AvailabilityPeriod period = new AvailabilityPeriod(
				provider,
				form.getPlanningFrom(),
				form.getPlanningTo(),
				form.getProviderTimezone()
				);

		AvailabilityPeriod savedPeriod = availabilityPeriodRepository.save(period);

		saveRuleIfComplete(savedPeriod, AvailabilityDay.MONDAY, form.getMondayStart(), form.getMondayEnd());
		saveRuleIfComplete(savedPeriod, AvailabilityDay.TUESDAY, form.getTuesdayStart(), form.getTuesdayEnd());
		saveRuleIfComplete(savedPeriod, AvailabilityDay.WEDNESDAY, form.getWednesdayStart(), form.getWednesdayEnd());
		saveRuleIfComplete(savedPeriod, AvailabilityDay.THURSDAY, form.getThursdayStart(), form.getThursdayEnd());
		saveRuleIfComplete(savedPeriod, AvailabilityDay.FRIDAY, form.getFridayStart(), form.getFridayEnd());
	}

	/**
	 * Validates basic calendar form consistency.
	 *
	 * @param form calendar form input
	 */
	private void validateForm(ProviderCalendarForm form) {
		Map<String, String> errors = new HashMap<>();

		if (form.getPlanningFrom() == null) {
			errors.put("planningFrom", "Start date is required");
		}
		
		if (form.getPlanningFrom() != null
		        && form.getProviderTimezone() != null
		        && !form.getProviderTimezone().isBlank()) {

		    LocalDate today = LocalDate.now(ZoneId.of(form.getProviderTimezone()));

		    if (form.getPlanningFrom().isBefore(today)) {
		        errors.put("planningFrom", "Start date must not be before today");
		    }
		}

		if (form.getPlanningTo() == null) {
			errors.put("planningTo", "End date is required");
		}

		if (form.getPlanningTo() != null
		        && form.getProviderTimezone() != null
		        && !form.getProviderTimezone().isBlank()) {

		    LocalDate today = LocalDate.now(ZoneId.of(form.getProviderTimezone()));

		    if (form.getPlanningTo().isBefore(today)) {
		        errors.put("planningTo", "End date must not be before today");
		    }
		}
		
		if (form.getPlanningFrom() != null
				&& form.getPlanningTo() != null
				&& form.getPlanningTo().isBefore(form.getPlanningFrom())) {
			errors.put("planningTo", "End date must not be before start date");
		}

		if (form.getProviderTimezone() == null || form.getProviderTimezone().isBlank()) {
			errors.put("providerTimezone", "Timezone is required");
		}

		validateTimeRange(errors, "monday", form.getMondayStart(), form.getMondayEnd(), "Monday");
		validateTimeRange(errors, "tuesday", form.getTuesdayStart(), form.getTuesdayEnd(), "Tuesday");
		validateTimeRange(errors, "wednesday", form.getWednesdayStart(), form.getWednesdayEnd(), "Wednesday");
		validateTimeRange(errors, "thursday", form.getThursdayStart(), form.getThursdayEnd(), "Thursday");
		validateTimeRange(errors, "friday", form.getFridayStart(), form.getFridayEnd(), "Friday");

		if (!errors.isEmpty()) {
			throw new CalendarValidationException(errors);
		}
	}
	/**
	 * Validates one weekday time range.
	 *
	 * @param start start time
	 * @param end end time
	 * @param dayLabel day label for error message
	 */
	private void validateTimeRange(Map<String, String> errors,
			String fieldPrefix,
			LocalTime start,
			LocalTime end,
			String dayLabel) {
		if (start == null && end == null) {
			return;
		}

		if (isEmptyDay(start, end)) {
			return;
		}

		if (start == null || end == null) {
			errors.put(fieldPrefix, dayLabel + " must have both start and end time");
			return;
		}

		if (!end.isAfter(start)) {
			errors.put(fieldPrefix, dayLabel + " end time must be after start time");
		}
	}    
	/**
	 * Checks whether weekday input should be treated as empty.
	 *
	 * @param start start time
	 * @param end end time
	 * @return true if both values represent empty availability
	 */
	private boolean isEmptyDay(LocalTime start, LocalTime end) {
	    return LocalTime.MIDNIGHT.equals(start) && LocalTime.MIDNIGHT.equals(end);
	}
	
	/**
	 * Saves availability rule only when both start and end time are present.
	 *
	 * @param period availability period
	 * @param day day of week
	 * @param start start time
	 * @param end end time
	 */
	private void saveRuleIfComplete(AvailabilityPeriod period,
			AvailabilityDay day,
			LocalTime start,
			LocalTime end) {
		if (isEmptyDay(start, end)) {
			return;
		}

		if (start == null || end == null) {
			return;
		}

		availabilityRuleRepository.save(new AvailabilityRule(period, day, start, end));
	}

	/**
	 * Returns latest saved provider calendar as form object.
	 *
	 * @param provider provider who owns the calendar
	 * @return calendar form filled from latest saved availability period
	 */
	@Transactional(readOnly = true)
	public ProviderCalendarForm getLatestCalendarForm(User provider) {
		Optional<AvailabilityPeriod> periodOptional =
				availabilityPeriodRepository.findTopByProviderOrderByCreatedAtDesc(provider);

		if (periodOptional.isEmpty()) {
			return new ProviderCalendarForm();
		}

		AvailabilityPeriod period = periodOptional.get();

		ProviderCalendarForm form = new ProviderCalendarForm();
		form.setPlanningFrom(period.getDateFrom());
		form.setPlanningTo(period.getDateTo());
		form.setProviderTimezone(period.getProviderTimezone());

		availabilityRuleRepository.findAllByAvailabilityPeriod(period)
		.forEach(rule -> applyRuleToForm(form, rule));

		return form;
	}

	/**
	 * Applies saved availability rule to calendar form.
	 *
	 * @param form calendar form
	 * @param rule saved availability rule
	 */
	private void applyRuleToForm(ProviderCalendarForm form, AvailabilityRule rule) {
		if (rule.getDayOfWeek() == AvailabilityDay.MONDAY) {
			form.setMondayStart(rule.getStartTime());
			form.setMondayEnd(rule.getEndTime());
		}

		if (rule.getDayOfWeek() == AvailabilityDay.TUESDAY) {
			form.setTuesdayStart(rule.getStartTime());
			form.setTuesdayEnd(rule.getEndTime());
		}

		if (rule.getDayOfWeek() == AvailabilityDay.WEDNESDAY) {
			form.setWednesdayStart(rule.getStartTime());
			form.setWednesdayEnd(rule.getEndTime());
		}

		if (rule.getDayOfWeek() == AvailabilityDay.THURSDAY) {
			form.setThursdayStart(rule.getStartTime());
			form.setThursdayEnd(rule.getEndTime());
		}

		if (rule.getDayOfWeek() == AvailabilityDay.FRIDAY) {
			form.setFridayStart(rule.getStartTime());
			form.setFridayEnd(rule.getEndTime());
		}
	}

	/**
	 * Generates provider availability calendar terms excluding past dates.
	 *
	 * @param provider provider who owns availability calendar
	 * @return future and current availability terms ordered by date and start time
	 */
	@Transactional(readOnly = true)
	public List<CalendarTerm> generateCalendar(User provider) {
	    Optional<AvailabilityPeriod> periodOpt =
	            availabilityPeriodRepository.findTopByProviderOrderByCreatedAtDesc(provider);

	    if (periodOpt.isEmpty()) {
	        return List.of();
	    }

	    AvailabilityPeriod period = periodOpt.get();

	    List<AvailabilityRule> rules =
	            availabilityRuleRepository.findAllByAvailabilityPeriod(period);

	    List<CalendarTerm> result = new ArrayList<>();

	    LocalDate today = LocalDate.now(ZoneId.of(period.getProviderTimezone()));
	    LocalDate current = period.getDateFrom().isBefore(today)
	            ? today
	            : period.getDateFrom();

	    while (!current.isAfter(period.getDateTo())) {
	        for (AvailabilityRule rule : rules) {
	            if (current.getDayOfWeek().name().equals(rule.getDayOfWeek().name())) {
	                result.add(new CalendarTerm(
	                        current,
	                        rule.getStartTime(),
	                        rule.getEndTime()
	                ));
	            }
	        }

	        current = current.plusDays(1);
	    }

	    result.sort(Comparator.comparing(CalendarTerm::getDate)
	            .thenComparing(CalendarTerm::getStartTime));

	    return result;
	}
	
	/**
	 * Checks whether requested appointment time is inside provider availability.
	 *
	 * @param provider provider who owns availability
	 * @param startDateTimeUtc requested appointment start in UTC
	 * @param durationMinutes appointment duration in minutes
	 * @return true if appointment fits into provider availability
	 */
	@Transactional(readOnly = true)
	public boolean isAvailable(User provider,
	                           LocalDateTime startDateTimeUtc,
	                           Integer durationMinutes) {
	    if (provider == null || startDateTimeUtc == null || durationMinutes == null || durationMinutes <= 0) {
	        return false;
	    }

	    Optional<AvailabilityPeriod> periodOptional =
	            availabilityPeriodRepository.findTopByProviderOrderByCreatedAtDesc(provider);

	    if (periodOptional.isEmpty()) {
	        return false;
	    }

	    AvailabilityPeriod period = periodOptional.get();
	    ZoneId providerZone = ZoneId.of(period.getProviderTimezone());

	    ZonedDateTime startInProviderZone = startDateTimeUtc
	            .atZone(ZoneOffset.UTC)
	            .withZoneSameInstant(providerZone);

	    ZonedDateTime endInProviderZone = startInProviderZone.plusMinutes(durationMinutes);

	    LocalDate appointmentDate = startInProviderZone.toLocalDate();

	    if (appointmentDate.isBefore(period.getDateFrom()) || appointmentDate.isAfter(period.getDateTo())) {
	        return false;
	    }

	    if (!startInProviderZone.toLocalDate().equals(endInProviderZone.toLocalDate())) {
	        return false;
	    }

	    List<AvailabilityRule> rules = availabilityRuleRepository.findAllByAvailabilityPeriod(period);

	    return rules.stream().anyMatch(rule ->
	            rule.getDayOfWeek().name().equals(startInProviderZone.getDayOfWeek().name())
	                    && !startInProviderZone.toLocalTime().isBefore(rule.getStartTime())
	                    && !endInProviderZone.toLocalTime().isAfter(rule.getEndTime())
	    );
	}
	
	/**
	 * Returns timezone from latest provider availability period.
	 *
	 * @param provider provider user
	 * @return provider timezone
	 */
	@Transactional(readOnly = true)
	public ZoneId getProviderTimezone(User provider) {
	    AvailabilityPeriod period = availabilityPeriodRepository
	            .findTopByProviderOrderByCreatedAtDesc(provider)
	            .orElseThrow(() -> new IllegalArgumentException("Provider timezone not found"));

	    return ZoneId.of(period.getProviderTimezone());
	}
	
	/**
	 * Deletes provider availability periods that ended before today.
	 *
	 * @param provider provider who owns availability periods
	 */
	@Transactional
	public void deleteExpiredAvailabilityPeriods(User provider) {
	    List<AvailabilityPeriod> periods = availabilityPeriodRepository.findAllByProvider(provider);

	    for (AvailabilityPeriod period : periods) {
	        LocalDate today = LocalDate.now(ZoneId.of(period.getProviderTimezone()));

	        if (period.getDateTo().isBefore(today)) {
	            availabilityRuleRepository.deleteAllByAvailabilityPeriod(period);
	            availabilityPeriodRepository.delete(period);
	        }
	    }
	}
}