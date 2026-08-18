package life.wellnara.service.calendar;

import java.util.Objects;

/**
 * A ready-to-serve iCalendar download: its file name and serialized content.
 *
 * @param fileName download file name, e.g. {@code appointment-42.ics}
 * @param content  serialized RFC 5545 {@code VCALENDAR} document
 */
public record CalendarDownload(String fileName, String content) {

    /**
     * Validates that every component is present.
     *
     * @throws NullPointerException if any component is {@code null}
     */
    public CalendarDownload {
        Objects.requireNonNull(fileName, "fileName is required");
        Objects.requireNonNull(content, "content is required");
    }
}
