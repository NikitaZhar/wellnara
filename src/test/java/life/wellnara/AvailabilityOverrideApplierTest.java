package life.wellnara;

import life.wellnara.dto.CalendarTerm;
import life.wellnara.model.AvailabilityOverride;
import life.wellnara.model.AvailabilityOverrideType;
import life.wellnara.model.User;
import life.wellnara.service.AvailabilityOverrideApplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AvailabilityOverrideApplier.
 */
class AvailabilityOverrideApplierTest {

    private final AvailabilityOverrideApplier applier =
            new AvailabilityOverrideApplier();

    /**
     * Verifies that unavailable override splits one calendar term
     * into two remaining free terms.
     */
    @Test
    @DisplayName("Should split calendar term by unavailable override")
    void shouldSplitCalendarTermByUnavailableOverride() {
        LocalDate date = LocalDate.of(2026, 5, 11);

        List<CalendarTerm> baseTerms = List.of(
                new CalendarTerm(
                        date,
                        LocalTime.of(9, 0),
                        LocalTime.of(15, 0)
                )
        );

        AvailabilityOverride override = new AvailabilityOverride(
                new User(),
                date,
                LocalTime.of(11, 0),
                LocalTime.of(12, 0),
                AvailabilityOverrideType.UNAVAILABLE
        );

        List<CalendarTerm> result =
                applier.apply(baseTerms, List.of(override));

        assertThat(result)
                .extracting(term ->
                        term.getDate() + " "
                                + term.getStartTime() + "-"
                                + term.getEndTime())
                .containsExactly(
                        "2026-05-11 09:00-11:00",
                        "2026-05-11 12:00-15:00"
                );
    }

    /**
     * Verifies that available override adds one-time term
     * when base calendar is empty.
     */
    @Test
    @DisplayName("Should add available override to empty calendar")
    void shouldAddAvailableOverrideToEmptyCalendar() {
        LocalDate date = LocalDate.of(2026, 5, 12);

        AvailabilityOverride override = new AvailabilityOverride(
                new User(),
                date,
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                AvailabilityOverrideType.AVAILABLE
        );

        List<CalendarTerm> result =
                applier.apply(List.of(), List.of(override));

        assertThat(result)
                .extracting(term ->
                        term.getDate() + " "
                                + term.getStartTime() + "-"
                                + term.getEndTime())
                .containsExactly(
                        "2026-05-12 10:00-11:00"
                );
    }
}