package life.wellnara.service;

import life.wellnara.dto.CalendarTerm;
import life.wellnara.model.AvailabilityOverride;
import life.wellnara.model.AvailabilityOverrideType;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Applies one-time availability overrides to generated provider calendar terms.
 */
@Component
public class AvailabilityOverrideApplier {

    /**
     * Applies unavailable and available overrides to base calendar terms.
     *
     * @param baseTerms base provider calendar terms
     * @param overrides one-time availability overrides
     * @return calendar terms after applying overrides
     */
    public List<CalendarTerm> apply(List<CalendarTerm> baseTerms,
                                    List<AvailabilityOverride> overrides) {
        List<CalendarTerm> result = new ArrayList<>(baseTerms);

        for (AvailabilityOverride override : overrides) {
            if (override.getType() == AvailabilityOverrideType.UNAVAILABLE) {
                result = applyUnavailableOverride(result, override);
            }
        }

        for (AvailabilityOverride override : overrides) {
            if (override.getType() == AvailabilityOverrideType.AVAILABLE) {
                result.add(new CalendarTerm(
                        override.getOverrideDate(),
                        override.getStartTime(),
                        override.getEndTime()
                ));
            }
        }

        return mergeTerms(result);
    }

    private List<CalendarTerm> applyUnavailableOverride(List<CalendarTerm> terms,
                                                        AvailabilityOverride override) {
        List<CalendarTerm> result = new ArrayList<>();

        for (CalendarTerm term : terms) {
            if (!term.getDate().equals(override.getOverrideDate())
                    || !overlaps(
                            term.getStartTime(),
                            term.getEndTime(),
                            override.getStartTime(),
                            override.getEndTime()
                    )) {
                result.add(term);
                continue;
            }

            if (term.getStartTime().isBefore(override.getStartTime())) {
                result.add(new CalendarTerm(
                        term.getDate(),
                        term.getStartTime(),
                        override.getStartTime()
                ));
            }

            if (override.getEndTime().isBefore(term.getEndTime())) {
                result.add(new CalendarTerm(
                        term.getDate(),
                        override.getEndTime(),
                        term.getEndTime()
                ));
            }
        }

        return result;
    }

    private List<CalendarTerm> mergeTerms(List<CalendarTerm> terms) {
        List<CalendarTerm> sortedTerms = terms.stream()
                .sorted(Comparator.comparing(CalendarTerm::getDate)
                        .thenComparing(CalendarTerm::getStartTime))
                .toList();

        List<CalendarTerm> result = new ArrayList<>();

        for (CalendarTerm term : sortedTerms) {
            if (result.isEmpty()) {
                result.add(term);
                continue;
            }

            CalendarTerm last = result.get(result.size() - 1);

            if (last.getDate().equals(term.getDate())
                    && !term.getStartTime().isAfter(last.getEndTime())) {
                result.set(
                        result.size() - 1,
                        new CalendarTerm(
                                last.getDate(),
                                last.getStartTime(),
                                max(last.getEndTime(), term.getEndTime())
                        )
                );
            } else {
                result.add(term);
            }
        }

        return result;
    }

    private boolean overlaps(LocalTime firstStart,
                             LocalTime firstEnd,
                             LocalTime secondStart,
                             LocalTime secondEnd) {
        return firstStart.isBefore(secondEnd) && firstEnd.isAfter(secondStart);
    }

    private LocalTime max(LocalTime first, LocalTime second) {
        return first.isAfter(second) ? first : second;
    }
}