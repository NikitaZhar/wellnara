package life.wellnara;

import life.wellnara.dto.ProviderCalendarForm;
import life.wellnara.exception.CalendarValidationException;
import life.wellnara.model.AvailabilityOverrideType;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.service.ProviderCalendarValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for provider calendar validation.
 */
class ProviderCalendarValidatorTest {

    private final ProviderCalendarValidator validator = new ProviderCalendarValidator();
    
    private final LocalDate currentDate = LocalDate.of(2026, 1, 1);

    @Test
    @DisplayName("Should accept valid provider calendar form")
    void shouldAcceptValidProviderCalendarForm() {
        ProviderCalendarForm form = validForm();

        validator.validateCalendarForm(form, currentDate);
    }

    @Test
    @DisplayName("Should reject missing timezone")
    void shouldRejectMissingTimezone() {
        ProviderCalendarForm form = validForm();
        form.setProviderTimezone(null);

        assertThatThrownBy(() -> validator.validateCalendarForm(form, currentDate))
                .isInstanceOf(CalendarValidationException.class)
                .satisfies(exception -> {
                    CalendarValidationException validationException =
                            (CalendarValidationException) exception;

                    assertThat(validationException.getFieldErrors())
                            .containsEntry("providerTimezone", "Timezone is required");
                });
    }

    @Test
    @DisplayName("Should reject invalid timezone")
    void shouldRejectInvalidTimezone() {
        ProviderCalendarForm form = validForm();
        form.setProviderTimezone("Invalid/Timezone");

        assertThatThrownBy(() -> validator.validateCalendarForm(form, currentDate))
                .isInstanceOf(CalendarValidationException.class)
                .satisfies(exception -> {
                    CalendarValidationException validationException =
                            (CalendarValidationException) exception;

                    assertThat(validationException.getFieldErrors())
                            .containsEntry("providerTimezone", "Timezone is invalid");
                });
    }

    @Test
    @DisplayName("Should reject planning dates before today")
    void shouldRejectPlanningDatesBeforeToday() {
        ProviderCalendarForm form = validForm();
        form.setPlanningFrom(currentDate.minusDays(1));

        assertThatThrownBy(() -> validator.validateCalendarForm(form, currentDate))
                .isInstanceOf(CalendarValidationException.class)
                .satisfies(exception -> {
                    CalendarValidationException validationException =
                            (CalendarValidationException) exception;

                    assertThat(validationException.getFieldErrors())
                            .containsKey("planningFrom");
                });
    }

    @Test
    @DisplayName("Should reject end date before start date")
    void shouldRejectEndDateBeforeStartDate() {
    	LocalDate today = currentDate;

        ProviderCalendarForm form = validForm();
        form.setPlanningFrom(today.plusDays(5));
        form.setPlanningTo(today.plusDays(3));

        assertThatThrownBy(() -> validator.validateCalendarForm(form, currentDate))
                .isInstanceOf(CalendarValidationException.class)
                .satisfies(exception -> {
                    CalendarValidationException validationException =
                            (CalendarValidationException) exception;

                    assertThat(validationException.getFieldErrors())
                            .containsEntry("planningTo", "End date must not be before start date");
                });
    }

    @Test
    @DisplayName("Should reject weekday with start time only")
    void shouldRejectWeekdayWithStartTimeOnly() {
        ProviderCalendarForm form = validForm();
        form.setMondayStart(LocalTime.of(9, 0));
        form.setMondayEnd(null);

        assertThatThrownBy(() -> validator.validateCalendarForm(form, currentDate))
                .isInstanceOf(CalendarValidationException.class)
                .satisfies(exception -> {
                    CalendarValidationException validationException =
                            (CalendarValidationException) exception;

                    assertThat(validationException.getFieldErrors())
                            .containsEntry("monday", "Monday must have both start and end time");
                });
    }

    @Test
    @DisplayName("Should reject weekday end time before start time")
    void shouldRejectWeekdayEndTimeBeforeStartTime() {
        ProviderCalendarForm form = validForm();
        form.setTuesdayStart(LocalTime.of(14, 0));
        form.setTuesdayEnd(LocalTime.of(13, 0));

        assertThatThrownBy(() -> validator.validateCalendarForm(form, currentDate))
                .isInstanceOf(CalendarValidationException.class)
                .satisfies(exception -> {
                    CalendarValidationException validationException =
                            (CalendarValidationException) exception;

                    assertThat(validationException.getFieldErrors())
                            .containsEntry("tuesday", "Tuesday end time must be after start time");
                });
    }

    @Test
    @DisplayName("Should reject override with invalid time range")
    void shouldRejectOverrideWithInvalidTimeRange() {
        User provider = createProvider();

        assertThatThrownBy(() -> validator.validateAvailabilityOverride(
                provider,
                currentDate.plusDays(1),
                LocalTime.of(12, 0),
                LocalTime.of(11, 0),
                AvailabilityOverrideType.AVAILABLE,
                currentDate
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("End time must be after start time");
    }

    @Test
    @DisplayName("Should reject override with non 15-minute interval")
    void shouldRejectOverrideWithNonFifteenMinuteInterval() {
        User provider = createProvider();

        assertThatThrownBy(() -> validator.validateAvailabilityOverride(
                provider,
                currentDate.plusDays(1),
                LocalTime.of(10, 10),
                LocalTime.of(11, 0),
                AvailabilityOverrideType.AVAILABLE,
                currentDate
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Time must use 15-minute intervals");
    }

    private ProviderCalendarForm validForm() {
    	LocalDate today = currentDate;

        ProviderCalendarForm form = new ProviderCalendarForm();
        form.setPlanningFrom(today.plusDays(1));
        form.setPlanningTo(today.plusDays(14));
        form.setProviderTimezone("Europe/Bratislava");

        form.setMondayStart(LocalTime.of(9, 0));
        form.setMondayEnd(LocalTime.of(12, 0));

        return form;
    }

    private User createProvider() {
        User provider = new User();
        provider.setUsername("provider-validator-test");
        provider.setEmail("provider-validator-test@example.com");
        provider.setPassword("123");
        provider.setRole(UserRole.PROVIDER);
        return provider;
    }
}