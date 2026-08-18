package life.wellnara.service.calendar;

/**
 * Participant perspective a calendar event is generated for.
 *
 * <p>An appointment is mirrored into both participants' calendars, but each side
 * receives a manage link pointing to its own area of the application. The
 * audience selects the correct deep link when the event is built.
 */
public enum CalendarAudience {

    /** The provider who owns the appointment. */
    PROVIDER,

    /** The client who booked the appointment. */
    CLIENT
}
