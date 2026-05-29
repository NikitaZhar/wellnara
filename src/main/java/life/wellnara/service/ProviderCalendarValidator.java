package life.wellnara.service;

import life.wellnara.dto.ProviderCalendarForm;
import life.wellnara.exception.CalendarValidationException;
import life.wellnara.model.AvailabilityOverrideType;
import life.wellnara.model.User;
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
            throw new IllegalArgumentException("Provider is required");
        }

        if (date == null) {
            throw new IllegalArgumentException("Date is required");
        }

        if (currentDate == null) {
            throw new IllegalArgumentException("Current date is required");
        }

        if (date.isBefore(currentDate)) {
            throw new IllegalArgumentException("Date must not be in the past");
        }

        if (startTime == null) {
            throw new IllegalArgumentException("Start time is required");
        }

        if (endTime == null) {
            throw new IllegalArgumentException("End time is required");
        }

        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("End time must be after start time");
        }

        if (startTime.getMinute() % 15 != 0 || endTime.getMinute() % 15 != 0) {
            throw new IllegalArgumentException("Time must use 15-minute intervals");
        }

        if (type == null) {
            throw new IllegalArgumentException("Override type is required");
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
            errors.put("planningFrom", "Start date is required");
        }

        if (form.getPlanningTo() == null) {
            errors.put("planningTo", "End date is required");
        }

        if (form.getProviderTimezone() == null || form.getProviderTimezone().isBlank()) {
            errors.put("providerTimezone", "Timezone is required");
            return;
        }

        if (!isValidTimezone(form.getProviderTimezone())) {
            errors.put("providerTimezone", "Timezone is invalid");
        }
    }

    private void validateCalendarDateRange(Map<String, String> errors,
                                           ProviderCalendarForm form,
                                           LocalDate currentDate) {
        if (currentDate == null) {
            errors.put("currentDate", "Current date is required");
            return;
        }

        if (form.getPlanningFrom() != null && form.getPlanningFrom().isBefore(currentDate)) {
            errors.put("planningFrom", "Start date must not be before today");
        }

        if (form.getPlanningTo() != null && form.getPlanningTo().isBefore(currentDate)) {
            errors.put("planningTo", "End date must not be before today");
        }

        if (form.getPlanningFrom() != null
                && form.getPlanningTo() != null
                && form.getPlanningTo().isBefore(form.getPlanningFrom())) {
            errors.put("planningTo", "End date must not be before start date");
        }
    }

    private void validateWeekdayTimeRanges(Map<String, String> errors,
                                           ProviderCalendarForm form) {
        validateTimeRange(errors, "monday", form.getMondayStart(), form.getMondayEnd(), "Monday");
        validateTimeRange(errors, "tuesday", form.getTuesdayStart(), form.getTuesdayEnd(), "Tuesday");
        validateTimeRange(errors, "wednesday", form.getWednesdayStart(), form.getWednesdayEnd(), "Wednesday");
        validateTimeRange(errors, "thursday", form.getThursdayStart(), form.getThursdayEnd(), "Thursday");
        validateTimeRange(errors, "friday", form.getFridayStart(), form.getFridayEnd(), "Friday");
    }

    private void validateTimeRange(Map<String, String> errors,
                                   String fieldPrefix,
                                   LocalTime start,
                                   LocalTime end,
                                   String dayLabel) {
        if (start == null && end == null) {
            return;
        }

        if (isEmptyAvailabilityDay(start, end)) {
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

    private boolean isValidTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
            return true;
        } catch (DateTimeException exception) {
            return false;
        }
    }
}