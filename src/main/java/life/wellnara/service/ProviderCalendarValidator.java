package life.wellnara.service;

import life.wellnara.dto.ProviderCalendarForm;
import life.wellnara.exception.CalendarValidationException;
import life.wellnara.exception.LocalizedException;
import life.wellnara.model.AvailabilityOverrideType;
import life.wellnara.model.User;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * Validates provider calendar and one-time availability override input.
 */
@Component
public class ProviderCalendarValidator {

    private final MessageSource messageSource;

    /**
     * Creates provider calendar validator.
     *
     * @param messageSource resolver of localized user-facing messages
     */
    public ProviderCalendarValidator(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    /**
     * Validates provider calendar form.
     *
     * @param form provider calendar form
     * @param currentDate current date in provider calendar timezone
     */
    public void validateCalendarForm(ProviderCalendarForm form, LocalDate currentDate) {
        Map<String, String> errors = new HashMap<>();

        validateRequiredCalendarFields(errors, form);
        validateCalendarDateRange(errors, form, currentDate);
        validateWeekdayTimeRanges(errors, form);

        if (!errors.isEmpty()) {
            throw new CalendarValidationException(errors);
        }
    }

    /**
     * Validates one-time availability override input.
     *
     * @param provider provider who owns the override
     * @param date override date
     * @param startTime override start time
     * @param endTime override end time
     * @param type override type
     * @param currentDate current date in provider calendar timezone
     */
    public void validateAvailabilityOverride(User provider,
                                             LocalDate date,
                                             LocalTime startTime,
                                             LocalTime endTime,
                                             AvailabilityOverrideType type,
                                             LocalDate currentDate) {
        if (provider == null) {
            throw new LocalizedException("error.calendar.providerRequired", "Provider is required");
        }

        if (date == null) {
            throw new LocalizedException("error.calendar.dateRequired", "Date is required");
        }

        if (currentDate == null) {
            throw new LocalizedException("error.calendar.currentDateRequired", "Current date is required");
        }

        if (date.isBefore(currentDate)) {
            throw new LocalizedException("error.calendar.datePast", "Date must not be in the past");
        }

        if (startTime == null) {
            throw new LocalizedException("error.calendar.startRequired", "Start time is required");
        }

        if (endTime == null) {
            throw new LocalizedException("error.calendar.endRequired", "End time is required");
        }

        if (!endTime.isAfter(startTime)) {
            throw new LocalizedException("error.calendar.endAfterStart", "End time must be after start time");
        }

        if (startTime.getMinute() % 15 != 0 || endTime.getMinute() % 15 != 0) {
            throw new LocalizedException("error.calendar.interval15", "Time must use 15-minute intervals");
        }

        if (type == null) {
            throw new LocalizedException("error.calendar.typeRequired", "Override type is required");
        }
    }

    /**
     * Checks whether weekday availability input represents an empty day.
     *
     * @param start start time
     * @param end end time
     * @return true when both values are midnight
     */
    public boolean isEmptyAvailabilityDay(LocalTime start, LocalTime end) {
        return LocalTime.MIDNIGHT.equals(start) && LocalTime.MIDNIGHT.equals(end);
    }

    private void validateRequiredCalendarFields(Map<String, String> errors,
                                                ProviderCalendarForm form) {
        if (form.getPlanningFrom() == null) {
            errors.put("planningFrom", msg("error.calendar.planningFromRequired"));
        }

        if (form.getPlanningTo() == null) {
            errors.put("planningTo", msg("error.calendar.planningToRequired"));
        }

        if (form.getProviderTimezone() == null || form.getProviderTimezone().isBlank()) {
            errors.put("providerTimezone", msg("error.calendar.timezoneRequired"));
            return;
        }

        if (!isValidTimezone(form.getProviderTimezone())) {
            errors.put("providerTimezone", msg("error.calendar.timezoneInvalid"));
        }
    }

    private void validateCalendarDateRange(Map<String, String> errors,
                                           ProviderCalendarForm form,
                                           LocalDate currentDate) {
        if (currentDate == null) {
            errors.put("currentDate", msg("error.calendar.currentDateRequired"));
            return;
        }

        if (form.getPlanningFrom() != null && form.getPlanningFrom().isBefore(currentDate)) {
            errors.put("planningFrom", msg("error.calendar.planningFromPast"));
        }

        if (form.getPlanningTo() != null && form.getPlanningTo().isBefore(currentDate)) {
            errors.put("planningTo", msg("error.calendar.planningToPast"));
        }

        if (form.getPlanningFrom() != null
                && form.getPlanningTo() != null
                && form.getPlanningTo().isBefore(form.getPlanningFrom())) {
            errors.put("planningTo", msg("error.calendar.planningToBeforeFrom"));
        }
    }

    private void validateWeekdayTimeRanges(Map<String, String> errors,
                                           ProviderCalendarForm form) {
        validateTimeRange(errors, "monday", form.getMondayStart(), form.getMondayEnd(), "dayOfWeek.MONDAY");
        validateTimeRange(errors, "tuesday", form.getTuesdayStart(), form.getTuesdayEnd(), "dayOfWeek.TUESDAY");
        validateTimeRange(errors, "wednesday", form.getWednesdayStart(), form.getWednesdayEnd(), "dayOfWeek.WEDNESDAY");
        validateTimeRange(errors, "thursday", form.getThursdayStart(), form.getThursdayEnd(), "dayOfWeek.THURSDAY");
        validateTimeRange(errors, "friday", form.getFridayStart(), form.getFridayEnd(), "dayOfWeek.FRIDAY");
    }

    private void validateTimeRange(Map<String, String> errors,
                                   String fieldPrefix,
                                   LocalTime start,
                                   LocalTime end,
                                   String dayKey) {
        if (start == null && end == null) {
            return;
        }

        if (isEmptyAvailabilityDay(start, end)) {
            return;
        }

        if (start == null || end == null) {
            errors.put(fieldPrefix, msg("error.calendar.dayBothTimes", msg(dayKey)));
            return;
        }

        if (!end.isAfter(start)) {
            errors.put(fieldPrefix, msg("error.calendar.dayEndAfterStart", msg(dayKey)));
        }
    }

    private boolean isValidTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
            return true;
        } catch (DateTimeException exception) {
            return false;
        }
    }
}