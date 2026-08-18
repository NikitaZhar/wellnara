package life.wellnara.service.email;

import life.wellnara.service.calendar.CalendarMethod;

import java.util.Objects;

/**
 * An iCalendar document to attach to an email.
 *
 * <p>The {@code method} is surfaced in the attachment's {@code text/calendar}
 * content type ({@code method=REQUEST} / {@code method=CANCEL}) so receiving
 * mail clients treat the payload as an add or a cancel of the same event.
 *
 * @param fileName attachment file name, e.g. {@code appointment-42.ics}
 * @param method   iCalendar method the payload was serialized with
 * @param content  serialized RFC 5545 {@code VCALENDAR} document
 */
public record CalendarAttachment(String fileName, CalendarMethod method, String content) {

    /**
     * Validates that every component is present.
     *
     * @throws NullPointerException if any component is {@code null}
     */
    public CalendarAttachment {
        Objects.requireNonNull(fileName, "fileName is required");
        Objects.requireNonNull(method, "method is required");
        Objects.requireNonNull(content, "content is required");
    }
}
