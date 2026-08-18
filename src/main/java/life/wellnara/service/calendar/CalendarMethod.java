package life.wellnara.service.calendar;

/**
 * iCalendar {@code METHOD} property applied to a serialized calendar payload.
 *
 * <p>The method tells the receiving calendar client how to treat the payload,
 * following RFC 5545 / RFC 5546 semantics. It is chosen by the caller at
 * serialization time, independently of the event contents.
 */
public enum CalendarMethod {

    /** Standalone publication of events, e.g. a personal subscription feed. */
    PUBLISH,

    /** Delivers or updates a single event, e.g. an appointment confirmation. */
    REQUEST,

    /** Withdraws a previously delivered event, e.g. an appointment cancellation. */
    CANCEL
}
