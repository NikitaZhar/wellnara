package life.wellnara.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Available booking times for one concrete free calendar term.
 */
public class BookableDateOption {

    private final LocalDate date;
    private final List<LocalTime> times;

    public BookableDateOption(LocalDate date, List<LocalTime> times) {
        this.date = date;
        this.times = times;
    }

    public LocalDate getDate() {
        return date;
    }

    public List<LocalTime> getTimes() {
        return times;
    }

    public String getTimesValue() {
        return times.stream()
                .map(LocalTime::toString)
                .collect(Collectors.joining(","));
    }
}