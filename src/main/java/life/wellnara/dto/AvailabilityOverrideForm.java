package life.wellnara.dto;

import life.wellnara.model.AvailabilityOverrideType;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One staged one-time availability change submitted as part of
 * {@link ProviderCalendarForm}. Not a domain model.
 */
public class AvailabilityOverrideForm {

    private AvailabilityOverrideType type;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;

    public AvailabilityOverrideType getType() {
        return type;
    }

    public void setType(AvailabilityOverrideType type) {
        this.type = type;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }
}
