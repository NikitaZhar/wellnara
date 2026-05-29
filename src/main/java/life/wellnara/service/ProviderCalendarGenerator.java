package life.wellnara.service;

import life.wellnara.dto.CalendarTerm;
import life.wellnara.model.AvailabilityPeriod;
import life.wellnara.model.AvailabilityRule;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Generates base provider calendar terms from availability period and weekly rules.
 */
@Component
public class ProviderCalendarGenerator {

    /**
     * Generates base calendar terms without one-time overrides.
     *
     * @param period provider availability period
     * @param rules weekly availability rules
     * @param currentDate current date in provider calendar timezone
     * @return generated calendar terms ordered by date and start time
     */
    public List<CalendarTerm> generate(AvailabilityPeriod period,
                                       List<AvailabilityRule> rules,
                                       LocalDate currentDate) {
        List<CalendarTerm> result = new ArrayList<>();
        LocalDate current = getFirstVisibleDate(period, currentDate);

        while (!current.isAfter(period.getDateTo())) {
            addTermsForDate(result, current, rules);
            current = current.plusDays(1);
        }

        result.sort(Comparator.comparing(CalendarTerm::getDate)
                .thenComparing(CalendarTerm::getStartTime));

        return result;
    }

    private LocalDate getFirstVisibleDate(AvailabilityPeriod period, LocalDate currentDate) {
        if (period.getDateFrom().isBefore(currentDate)) {
            return currentDate;
        }

        return period.getDateFrom();
    }

    private void addTermsForDate(List<CalendarTerm> result,
                                 LocalDate date,
                                 List<AvailabilityRule> rules) {
        for (AvailabilityRule rule : rules) {
            if (date.getDayOfWeek().name().equals(rule.getDayOfWeek().name())) {
                result.add(new CalendarTerm(
                        date,
                        rule.getStartTime(),
                        rule.getEndTime()
                ));
            }
        }
    }
}