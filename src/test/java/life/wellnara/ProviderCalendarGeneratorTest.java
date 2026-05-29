package life.wellnara;

import life.wellnara.dto.CalendarTerm;
import life.wellnara.model.AvailabilityDay;
import life.wellnara.model.AvailabilityPeriod;
import life.wellnara.model.AvailabilityRule;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.service.ProviderCalendarGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for provider calendar term generation.
 */
class ProviderCalendarGeneratorTest {

    private final ProviderCalendarGenerator generator = new ProviderCalendarGenerator();
    private final LocalDate currentDate = LocalDate.of(2026, 1, 1);

    @Test
    @DisplayName("Should generate calendar terms from availability period and weekly rules")
    void shouldGenerateCalendarTermsFromPeriodAndRules() {
        LocalDate nextMonday = nextOrSame(DayOfWeek.MONDAY);
        LocalDate nextFriday = nextMonday.plusDays(4);

        AvailabilityPeriod period = new AvailabilityPeriod(
                createProvider(),
                nextMonday,
                nextFriday,
                "Europe/Bratislava"
        );

        List<AvailabilityRule> rules = List.of(
                new AvailabilityRule(
                        period,
                        AvailabilityDay.MONDAY,
                        LocalTime.of(9, 0),
                        LocalTime.of(12, 0)
                ),
                new AvailabilityRule(
                        period,
                        AvailabilityDay.WEDNESDAY,
                        LocalTime.of(14, 0),
                        LocalTime.of(17, 0)
                )
        );

        List<CalendarTerm> terms = generator.generate(period, rules, currentDate);

        assertThat(terms).hasSize(2);

        assertThat(terms.get(0).getDate()).isEqualTo(nextMonday);
        assertThat(terms.get(0).getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(terms.get(0).getEndTime()).isEqualTo(LocalTime.of(12, 0));

        assertThat(terms.get(1).getDate()).isEqualTo(nextMonday.plusDays(2));
        assertThat(terms.get(1).getStartTime()).isEqualTo(LocalTime.of(14, 0));
        assertThat(terms.get(1).getEndTime()).isEqualTo(LocalTime.of(17, 0));
    }

    @Test
    @DisplayName("Should not generate terms before today")
    void shouldNotGenerateTermsBeforeToday() {
        AvailabilityPeriod period = new AvailabilityPeriod(
                createProvider(),
                currentDate.minusDays(7),
                currentDate.plusDays(7),
                "Europe/Bratislava"
        );

        AvailabilityDay todayAvailabilityDay =
                AvailabilityDay.valueOf(currentDate.getDayOfWeek().name());

        List<AvailabilityRule> rules = List.of(
                new AvailabilityRule(
                        period,
                        todayAvailabilityDay,
                        LocalTime.of(10, 0),
                        LocalTime.of(11, 0)
                )
        );

        List<CalendarTerm> terms = generator.generate(period, rules, currentDate);

        assertThat(terms).isNotEmpty();
        assertThat(terms)
                .allMatch(term -> !term.getDate().isBefore(currentDate));
    }

    @Test
    @DisplayName("Should return empty list when no rules match period dates")
    void shouldReturnEmptyListWhenNoRulesMatchPeriodDates() {
        LocalDate nextSaturday = nextOrSame(DayOfWeek.SATURDAY);
        LocalDate nextSunday = nextSaturday.plusDays(1);

        AvailabilityPeriod period = new AvailabilityPeriod(
                createProvider(),
                nextSaturday,
                nextSunday,
                "Europe/Bratislava"
        );

        List<AvailabilityRule> rules = List.of(
                new AvailabilityRule(
                        period,
                        AvailabilityDay.MONDAY,
                        LocalTime.of(9, 0),
                        LocalTime.of(12, 0)
                )
        );

        List<CalendarTerm> terms = generator.generate(period, rules, currentDate);

        assertThat(terms).isEmpty();
    }

    private LocalDate nextOrSame(DayOfWeek dayOfWeek) {
        return currentDate.with(TemporalAdjusters.nextOrSame(dayOfWeek));
    }

    private User createProvider() {
        User provider = new User();
        provider.setUsername("provider-generator-test");
        provider.setEmail("provider-generator-test@example.com");
        provider.setPassword("123");
        provider.setRole(UserRole.PROVIDER);
        return provider;
    }
}