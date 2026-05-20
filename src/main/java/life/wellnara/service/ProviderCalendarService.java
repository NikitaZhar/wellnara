package life.wellnara.service;

import life.wellnara.dto.CalendarTerm;
import life.wellnara.dto.ProviderCalendarForm;
import life.wellnara.model.AvailabilityDay;
import life.wellnara.model.AvailabilityOverride;
import life.wellnara.model.AvailabilityOverrideType;
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
import java.util.List;
import java.util.Optional;

/**
 * Service for provider calendar availability management.
 */
@Service
public class ProviderCalendarService {

    private static final ZoneId DEFAULT_PROVIDER_TIMEZONE = ZoneId.of("Europe/Bratislava");

    private final AvailabilityPeriodRepository availabilityPeriodRepository;
    private final AvailabilityRuleRepository availabilityRuleRepository;
    private final ProviderCalendarValidator calendarValidator;
    private final ProviderCalendarGenerator calendarGenerator;
    private final AvailabilityOverrideService availabilityOverrideService;
    private final AvailabilityOverrideApplier availabilityOverrideApplier;

    /**
     * Creates provider calendar service.
     *
     * @param availabilityPeriodRepository repository for availability periods
     * @param availabilityRuleRepository repository for availability rules
     * @param calendarValidator validator for provider calendar input
     * @param calendarGenerator generator for base calendar terms
     * @param availabilityOverrideService service for one-time availability overrides
     * @param availabilityOverrideApplier component that applies overrides to calendar terms
     */
    public ProviderCalendarService(AvailabilityPeriodRepository availabilityPeriodRepository,
                                   AvailabilityRuleRepository availabilityRuleRepository,
                                   ProviderCalendarValidator calendarValidator,
                                   ProviderCalendarGenerator calendarGenerator,
                                   AvailabilityOverrideService availabilityOverrideService,
                                   AvailabilityOverrideApplier availabilityOverrideApplier) {
        this.availabilityPeriodRepository = availabilityPeriodRepository;
        this.availabilityRuleRepository = availabilityRuleRepository;
        this.calendarValidator = calendarValidator;
        this.calendarGenerator = calendarGenerator;
        this.availabilityOverrideService = availabilityOverrideService;
        this.availabilityOverrideApplier = availabilityOverrideApplier;
    }

    /**
     * Saves provider calendar availability from form input.
     *
     * @param provider provider who owns the calendar
     * @param form calendar form input
     */
    @Transactional
    public void saveCalendar(User provider, ProviderCalendarForm form) {
        calendarValidator.validateCalendarForm(form);

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
        List<CalendarTerm> baseTerms = calendarGenerator.generate(period, rules);
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
     * Returns timezone from latest provider availability period.
     *
     * @param provider provider user
     * @return provider timezone
     */
    @Transactional(readOnly = true)
    public ZoneId getProviderTimezone(User provider) {
        return availabilityPeriodRepository
                .findTopByProviderOrderByCreatedAtDesc(provider)
                .map(AvailabilityPeriod::getProviderTimezone)
                .map(ZoneId::of)
                .orElse(DEFAULT_PROVIDER_TIMEZONE);
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
}