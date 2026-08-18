package life.wellnara.service.calendar;

/**
 * State of a calendar event, mapped to the iCalendar {@code STATUS} property.
 */
public enum CalendarEventStatus {

    /** The event is confirmed and should appear in the calendar. */
    CONFIRMED,

    /** The event is cancelled and should be removed from the calendar. */
    CANCELLED
}
