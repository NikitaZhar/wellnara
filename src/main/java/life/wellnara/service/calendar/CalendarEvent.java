package life.wellnara.service.calendar;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable, calendar-technology-neutral representation of a single event.
 *
 * <p>It carries exactly what {@link ICalendarSerializer} needs to emit an
 * RFC 5545 {@code VEVENT} and nothing appointment-specific, keeping the
 * serializer decoupled from the domain model. Instants are always understood
 * as UTC; the serializer renders them with a trailing {@code Z} and each
 * receiving calendar converts them to the device time zone.
 *
 * @param uid         globally unique, stable event identifier; a later payload
 *                    with the same {@code uid} updates or cancels the same event
 * @param sequence    revision counter; must not decrease across updates of the
 *                    same {@code uid} so clients accept the newer payload
 * @param timestamp   moment the payload was generated ({@code DTSTAMP})
 * @param start       event start instant ({@code DTSTART})
 * @param end         event end instant ({@code DTEND})
 * @param summary     human-readable title ({@code SUMMARY})
 * @param description human-readable details ({@code DESCRIPTION})
 * @param status      confirmed or cancelled state ({@code STATUS})
 */
public record CalendarEvent(
        String uid,
        long sequence,
        Instant timestamp,
        Instant start,
        Instant end,
        String summary,
        String description,
        CalendarEventStatus status) {

    /**
     * Validates that every reference-typed component is present.
     *
     * @throws NullPointerException if any component except {@code sequence} is {@code null}
     */
    public CalendarEvent {
        Objects.requireNonNull(uid, "uid is required");
        Objects.requireNonNull(timestamp, "timestamp is required");
        Objects.requireNonNull(start, "start is required");
        Objects.requireNonNull(end, "end is required");
        Objects.requireNonNull(summary, "summary is required");
        Objects.requireNonNull(description, "description is required");
        Objects.requireNonNull(status, "status is required");
    }
}
