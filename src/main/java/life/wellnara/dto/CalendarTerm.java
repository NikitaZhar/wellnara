package life.wellnara.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Represents one generated calendar slot.
 */
public class CalendarTerm {

    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;

    public CalendarTerm(LocalDate date, LocalTime startTime, LocalTime endTime) {
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public LocalDate getDate() {
        return date;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }
}