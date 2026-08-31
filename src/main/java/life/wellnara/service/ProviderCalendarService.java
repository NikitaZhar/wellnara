package life.wellnara.service;

import life.wellnara.dto.AvailabilityOverrideForm;
import life.wellnara.dto.CalendarTerm;
import life.wellnara.dto.ProviderCalendarForm;
import life.wellnara.exception.CalendarValidationException;
import life.wellnara.exception.LocalizedException;
import life.wellnara.model.AvailabilityDay;
import life.wellnara.model.AvailabilityOverride;
import life.wellnara.model.AvailabilityOverrideType;
import life.wellnara.model.AvailabilityPeriod;
import life.wellnara.model.AvailabilityRule;
import life.wellnara.model.User;
import life.wellnara.repository.AvailabilityPeriodRepository;
import life.wellnara.repository.AvailabilityRuleRepository;
import life.wellnara.service.time.ApplicationTimeService;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for provider calendar availability management.
 */
@Service
public class ProviderCalendarService {

	private final ApplicationTimeService applicationTimeService;

	private final AvailabilityPeriodRepository availabilityPeriodRepository;
	private final AvailabilityRuleRepository availabilityRuleRepository;
	private final ProviderCalendarValidator calendarValidator;
	private final ProviderCalendarGenerator calendarGenerator;
	private final AvailabilityOverrideService availabilityOverrideService;
	private final AvailabilityOverrideApplier availabilityOverrideApplier;
	private final MessageSource messageSource;

	/**
	 * Creates provider calendar service.
	 *
	 * @param availabilityPeriodRepository repository for availability periods
	 * @param availabilityRuleRepository repository for availability rules
	 * @param calendarValidator validator for provider calendar input
	 * @param calendarGenerator generator for base calendar terms
	 * @param availabilityOverrideService service for one-time availability overrides
	 * @param availabilityOverrideApplier component that applies overrides to calendar terms
	 * @param messageSource resolver of localized user-facing messages
	 */
	public ProviderCalendarService(AvailabilityPeriodRepository availabilityPeriodRepository,
			AvailabilityRuleRepository availabilityRuleRepository,
			ProviderCalendarValidator calendarValidator,
			ProviderCalendarGenerator calendarGenerator,
			AvailabilityOverrideService availabilityOverrideService,
			AvailabilityOverrideApplier availabilityOverrideApplier,
			ApplicationTimeService applicationTimeService,
			MessageSource messageSource) {
		this.availabilityPeriodRepository = availabilityPeriodRepository;
		this.availabilityRuleRepository = availabilityRuleRepository;
		this.calendarValidator = calendarValidator;
		this.calendarGenerator = calendarGenerator;
		this.availabilityOverrideService = availabilityOverrideService;
		this.availabilityOverrideApplier = availabilityOverrideApplier;
		this.applicationTimeService = applicationTimeService;
		this.messageSource = messageSource;
	}

	/**
	 * Saves provider calendar availability from form input.
	 *
	 * @param provider provider who owns the calendar
	 * @param form calendar form input
	 */
	@Transactional
	public void saveCalendar(User provider, ProviderCalendarForm form) {
	    LocalDate currentDate = applicationTimeService.currentDate(resolveFormTimezoneOrDefault(form));

	    calendarValidator.validateCalendarForm(form, currentDate);
	    validateOverrides(provider, form.getOverrides(), currentDate);

	    clearCalendar(provider);
	    availabilityOverrideService.replaceOverrides(provider, form.getOverrides());

	    AvailabilityPeriod savedPeriod = availabilityPeriodRepository.save(
	            new AvailabilityPeriod(
	                    provider,
	                    form.getPlanningFrom(),
	                    form.getPlanningTo(),
	                    form.getProviderTimezone()
	            )
	    );

	    saveRuleIfComplete(savedPeriod, AvailabilityDay.MONDAY, form.getMondayStart(), form.getMondayEnd());
	    saveRuleIfComplete(savedPeriod, AvailabilityDay.TUESDAY, form.getTuesdayStart(), form.getTuesdayEnd());
	    saveRuleIfComplete(savedPeriod, AvailabilityDay.WEDNESDAY, form.getWednesdayStart(), form.getWednesdayEnd());
	    saveRuleIfComplete(savedPeriod, AvailabilityDay.THURSDAY, form.getThursdayStart(), form.getThursdayEnd());
	    saveRuleIfComplete(savedPeriod, AvailabilityDay.FRIDAY, form.getFridayStart(), form.getFridayEnd());
	}

	/**
	 * Generates the calendar terms for unsaved form input, applying one-time
	 * changes, without touching the database. Used for the live preview so the
	 * provider sees the result before pressing Save. Returns an empty list when
	 * the planning period or timezone is not yet valid.
	 *
	 * @param provider provider who owns the calendar
	 * @param form current calendar form input
	 * @return preview calendar terms ordered by date and start time
	 */
	@Transactional(readOnly = true)
	public List<CalendarTerm> previewCalendar(User provider, ProviderCalendarForm form) {
		ZoneId zone = resolveFormTimezone(form);

		if (zone == null
				|| form.getPlanningFrom() == null
				|| form.getPlanningTo() == null
				|| form.getPlanningTo().isBefore(form.getPlanningFrom())) {
			return List.of();
		}

		AvailabilityPeriod period = new AvailabilityPeriod(
				provider, form.getPlanningFrom(), form.getPlanningTo(), form.getProviderTimezone());

		List<CalendarTerm> baseTerms = calendarGenerator.generate(
				period, buildRules(period, form), applicationTimeService.currentDate(zone));

		List<CalendarTerm> terms = availabilityOverrideApplier.apply(baseTerms, buildOverrides(provider, form));

		return removePastTerms(terms, zone);
	}

	/**
	 * Drops calendar terms that already lie in the past: on the current day a term
	 * whose start time has passed is removed, while terms on future dates are kept.
	 * Shared by the saved-calendar and live-preview paths so the provider preview
	 * shows exactly what a client could still book.
	 *
	 * @param terms        generated calendar terms
	 * @param providerZone provider calendar timezone
	 * @return terms with past ones removed
	 */
	List<CalendarTerm> removePastTerms(List<CalendarTerm> terms, ZoneId providerZone) {
		LocalDate today = applicationTimeService.currentDate(providerZone);
		LocalTime now = applicationTimeService.currentTime(providerZone);

		return terms.stream()
				.filter(term -> term.getDate().isAfter(today)
						|| !term.getStartTime().isBefore(now))
				.toList();
	}

	/**
	 * Validates every staged one-time change and aggregates errors so an
	 * invalid entry aborts the whole Save without touching the database.
	 */
	private void validateOverrides(User provider, List<AvailabilityOverrideForm> items, LocalDate currentDate) {
		if (items == null || items.isEmpty()) {
			return;
		}

		Map<String, String> errors = new HashMap<>();

		for (int i = 0; i < items.size(); i++) {
			AvailabilityOverrideForm item = items.get(i);

			if (item == null || item.getType() == null) {
				continue;
			}

			try {
				calendarValidator.validateAvailabilityOverride(
						provider, item.getDate(), item.getStartTime(), item.getEndTime(), item.getType(), currentDate);
			} catch (IllegalArgumentException exception) {
				errors.put("override" + i, messageSource.getMessage("error.calendar.overridePrefix",
						new Object[]{i + 1, LocalizedException.resolve(exception, messageSource, LocaleContextHolder.getLocale())},
						LocaleContextHolder.getLocale()));
			}
		}

		if (!errors.isEmpty()) {
			throw new CalendarValidationException(errors);
		}
	}

	/** Deletes the provider's current planning periods and their rules. */
	private void clearCalendar(User provider) {
		List<AvailabilityPeriod> periods = availabilityPeriodRepository.findAllByProvider(provider);

		for (AvailabilityPeriod period : periods) {
			availabilityRuleRepository.deleteAllByAvailabilityPeriod(period);
		}

		availabilityPeriodRepository.deleteAll(periods);
	}

	/** Builds transient weekly rules from the form, skipping empty or invalid days. */
	private List<AvailabilityRule> buildRules(AvailabilityPeriod period, ProviderCalendarForm form) {
		List<AvailabilityRule> rules = new ArrayList<>();
		addRuleIfComplete(rules, period, AvailabilityDay.MONDAY, form.getMondayStart(), form.getMondayEnd());
		addRuleIfComplete(rules, period, AvailabilityDay.TUESDAY, form.getTuesdayStart(), form.getTuesdayEnd());
		addRuleIfComplete(rules, period, AvailabilityDay.WEDNESDAY, form.getWednesdayStart(), form.getWednesdayEnd());
		addRuleIfComplete(rules, period, AvailabilityDay.THURSDAY, form.getThursdayStart(), form.getThursdayEnd());
		addRuleIfComplete(rules, period, AvailabilityDay.FRIDAY, form.getFridayStart(), form.getFridayEnd());
		return rules;
	}

	private void addRuleIfComplete(List<AvailabilityRule> rules, AvailabilityPeriod period,
			AvailabilityDay day, LocalTime start, LocalTime end) {
		if (start == null || end == null
				|| calendarValidator.isEmptyAvailabilityDay(start, end)
				|| !end.isAfter(start)) {
			return;
		}

		rules.add(new AvailabilityRule(period, day, start, end));
	}

	/** Builds transient overrides from the form, skipping incomplete or invalid entries. */
	private List<AvailabilityOverride> buildOverrides(User provider, ProviderCalendarForm form) {
		List<AvailabilityOverride> overrides = new ArrayList<>();

		if (form.getOverrides() == null) {
			return overrides;
		}

		for (AvailabilityOverrideForm item : form.getOverrides()) {
			if (item == null || item.getType() == null || item.getDate() == null
					|| item.getStartTime() == null || item.getEndTime() == null
					|| !item.getEndTime().isAfter(item.getStartTime())) {
				continue;
			}

			overrides.add(new AvailabilityOverride(
					provider, item.getDate(), item.getStartTime(), item.getEndTime(), item.getType()));
		}

		return overrides;
	}

	private ZoneId resolveFormTimezone(ProviderCalendarForm form) {
		if (form.getProviderTimezone() == null || form.getProviderTimezone().isBlank()) {
			return null;
		}

		try {
			return ZoneId.of(form.getProviderTimezone());
		} catch (DateTimeException exception) {
			return null;
		}
	}

	private ZoneId resolveFormTimezoneOrDefault(ProviderCalendarForm form) {
	    if (form.getProviderTimezone() == null || form.getProviderTimezone().isBlank()) {
	        return ZoneOffset.UTC;
	    }

	    try {
	        return ZoneId.of(form.getProviderTimezone());
	    } catch (DateTimeException exception) {
	        return ZoneOffset.UTC;
	    }
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
	 * Generates provider availability calendar terms excluding past dates.
	 *
	 * @param provider provider who owns availability calendar
	 * @return future and current availability terms ordered by date and start time
	 */
	@Transactional(readOnly = true)
	public List<CalendarTerm> generateCalendar(User provider) {
		Optional<AvailabilityPeriod> periodOptional =
				availabilityPeriodRepository.findTopByProviderOrderByCreatedAtDesc(provider);

		if (periodOptional.isEmpty()) {
			return List.of();
		}

		AvailabilityPeriod period = periodOptional.get();
		List<AvailabilityRule> rules = availabilityRuleRepository.findAllByAvailabilityPeriod(period);
		List<CalendarTerm> baseTerms = calendarGenerator.generate(
		        period,
		        rules,
		        applicationTimeService.currentDate(ZoneId.of(period.getProviderTimezone()))
		);
		List<AvailabilityOverride> overrides = availabilityOverrideService.getAvailabilityOverrides(provider);

		return availabilityOverrideApplier.apply(baseTerms, overrides);
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
		if (provider == null
				|| startDateTimeUtc == null
				|| durationMinutes == null
				|| durationMinutes <= 0) {
			return false;
		}

		ZoneId providerZone = getProviderTimezone(provider);

		LocalDateTime localStart = startDateTimeUtc
				.atZone(ZoneOffset.UTC)
				.withZoneSameInstant(providerZone)
				.toLocalDateTime();

		LocalDateTime localEnd = localStart.plusMinutes(durationMinutes);

		if (!localStart.toLocalDate().equals(localEnd.toLocalDate())) {
			return false;
		}

		return generateCalendar(provider).stream()
				.anyMatch(term -> containsAppointmentTime(term, localStart, localEnd));
	}

	/**
	 * Returns timezone used by provider availability calendar.
	 *
	 * @param provider provider who owns the calendar
	 * @return provider calendar timezone
	 */
	@Transactional(readOnly = true)
	public ZoneId getProviderTimezone(User provider) {
	    return applicationTimeService.resolveProviderCalendarZone(provider);
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
			LocalDate today = applicationTimeService.currentDate(
			        ZoneId.of(period.getProviderTimezone())
			);

			if (period.getDateTo().isBefore(today)) {
				availabilityRuleRepository.deleteAllByAvailabilityPeriod(period);
				availabilityPeriodRepository.delete(period);
			}
		}
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
	public void createAvailabilityOverride(User provider,
			LocalDate date,
			LocalTime startTime,
			LocalTime endTime,
			AvailabilityOverrideType type) {
		availabilityOverrideService.createAvailabilityOverride(provider, date, startTime, endTime, type);
	}

	/**
	 * Deletes provider availability override.
	 *
	 * @param provider provider who owns the override
	 * @param overrideId override identifier
	 */
	public void deleteAvailabilityOverride(User provider, Long overrideId) {
		availabilityOverrideService.deleteAvailabilityOverride(provider, overrideId);
	}

	/**
	 * Returns provider availability overrides.
	 *
	 * @param provider provider who owns the overrides
	 * @return ordered provider availability overrides
	 */
	public List<AvailabilityOverride> getAvailabilityOverrides(User provider) {
		return availabilityOverrideService.getAvailabilityOverrides(provider);
	}

	private void saveRuleIfComplete(AvailabilityPeriod period,
			AvailabilityDay day,
			LocalTime start,
			LocalTime end) {
		if (calendarValidator.isEmptyAvailabilityDay(start, end) || start == null || end == null) {
			return;
		}

		availabilityRuleRepository.save(new AvailabilityRule(period, day, start, end));
	}

	private void applyRuleToForm(ProviderCalendarForm form, AvailabilityRule rule) {
		AvailabilityDay day = rule.getDayOfWeek();

		if (day == AvailabilityDay.MONDAY) {
			form.setMondayStart(rule.getStartTime());
			form.setMondayEnd(rule.getEndTime());
		}

		if (day == AvailabilityDay.TUESDAY) {
			form.setTuesdayStart(rule.getStartTime());
			form.setTuesdayEnd(rule.getEndTime());
		}

		if (day == AvailabilityDay.WEDNESDAY) {
			form.setWednesdayStart(rule.getStartTime());
			form.setWednesdayEnd(rule.getEndTime());
		}

		if (day == AvailabilityDay.THURSDAY) {
			form.setThursdayStart(rule.getStartTime());
			form.setThursdayEnd(rule.getEndTime());
		}

		if (day == AvailabilityDay.FRIDAY) {
			form.setFridayStart(rule.getStartTime());
			form.setFridayEnd(rule.getEndTime());
		}
	}

	private boolean containsAppointmentTime(CalendarTerm term,
			LocalDateTime localStart,
			LocalDateTime localEnd) {
		return term.getDate().equals(localStart.toLocalDate())
				&& !localStart.toLocalTime().isBefore(term.getStartTime())
				&& !localEnd.toLocalTime().isAfter(term.getEndTime());
	}

	/**
	 * Deletes provider availability overrides whose end date and time have already passed.
	 *
	 * @param provider provider who owns the overrides
	 */
	public void deleteExpiredAvailabilityOverrides(User provider) {
		availabilityOverrideService.deleteExpiredAvailabilityOverrides(provider);
	}
}