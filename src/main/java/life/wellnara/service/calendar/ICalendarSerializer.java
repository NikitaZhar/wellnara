package life.wellnara.service.calendar;

import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Serializes {@link CalendarEvent}s into an RFC 5545 {@code VCALENDAR} document.
 *
 * <p>The serializer is a pure function of its input: it adds no timestamps of
 * its own (each event already carries its {@code DTSTAMP}), which keeps its
 * output deterministic and easy to test. It handles the three cross-cutting
 * requirements of the format: UTC timestamp rendering, {@code TEXT} value
 * escaping, and content-line folding at 75 octets with CRLF line endings.
 */
@Component
public class ICalendarSerializer {

    private static final String PRODUCT_ID = "-//Wellnara//Appointments//EN";
    private static final String CRLF = "\r\n";
    private static final int MAX_LINE_OCTETS = 75;

    private static final DateTimeFormatter UTC_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    /**
     * Serializes a single event.
     *
     * @param event  event to serialize
     * @param method iCalendar method applied to the calendar
     * @return RFC 5545 {@code VCALENDAR} document
     */
    public String serialize(CalendarEvent event, CalendarMethod method) {
        Objects.requireNonNull(event, "event is required");

        return serialize(List.of(event), method);
    }

    /**
     * Serializes a list of events into one calendar document.
     *
     * @param events events to serialize, in the order they should appear
     * @param method iCalendar method applied to the calendar
     * @return RFC 5545 {@code VCALENDAR} document
     */
    public String serialize(List<CalendarEvent> events, CalendarMethod method) {
        Objects.requireNonNull(events, "events are required");
        Objects.requireNonNull(method, "method is required");

        StringBuilder builder = new StringBuilder();
        appendLine(builder, "BEGIN:VCALENDAR");
        appendLine(builder, "VERSION:2.0");
        appendLine(builder, "PRODID:" + PRODUCT_ID);
        appendLine(builder, "CALSCALE:GREGORIAN");
        appendLine(builder, "METHOD:" + method.name());

        for (CalendarEvent event : events) {
            appendEvent(builder, event);
        }

        appendLine(builder, "END:VCALENDAR");
        return builder.toString();
    }

    private void appendEvent(StringBuilder builder, CalendarEvent event) {
        Objects.requireNonNull(event, "event is required");

        appendLine(builder, "BEGIN:VEVENT");
        appendLine(builder, "UID:" + escapeText(event.uid()));
        appendLine(builder, "SEQUENCE:" + event.sequence());
        appendLine(builder, "DTSTAMP:" + UTC_TIMESTAMP.format(event.timestamp()));
        appendLine(builder, "DTSTART:" + UTC_TIMESTAMP.format(event.start()));
        appendLine(builder, "DTEND:" + UTC_TIMESTAMP.format(event.end()));
        appendLine(builder, "SUMMARY:" + escapeText(event.summary()));
        appendLine(builder, "DESCRIPTION:" + escapeText(event.description()));
        appendLine(builder, "STATUS:" + event.status().name());
        appendLine(builder, "END:VEVENT");
    }

    /**
     * Appends one content line, folded to the octet limit and terminated by CRLF.
     */
    private void appendLine(StringBuilder builder, String line) {
        builder.append(fold(line)).append(CRLF);
    }

    /**
     * Folds a content line so no physical line exceeds {@value #MAX_LINE_OCTETS}
     * octets, inserting {@code CRLF} followed by a single space at each split and
     * never splitting a multi-byte character.
     */
    private String fold(String line) {
        StringBuilder result = new StringBuilder();
        int lineOctets = 0;

        int index = 0;
        while (index < line.length()) {
            int codePoint = line.codePointAt(index);
            int octets = utf8Length(codePoint);

            if (lineOctets + octets > MAX_LINE_OCTETS) {
                result.append(CRLF).append(' ');
                lineOctets = 1;
            }

            result.appendCodePoint(codePoint);
            lineOctets += octets;
            index += Character.charCount(codePoint);
        }

        return result.toString();
    }

    /**
     * Escapes a {@code TEXT} value per RFC 5545: backslash, semicolon and comma
     * are prefixed with a backslash, and any line break becomes a literal
     * {@code \n}. Backslash is escaped first to avoid double-escaping.
     */
    private String escapeText(String value) {
        return value
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\r", "\\n")
                .replace("\n", "\\n");
    }

    private int utf8Length(int codePoint) {
        if (codePoint <= 0x7F) {
            return 1;
        }
        if (codePoint <= 0x7FF) {
            return 2;
        }
        if (codePoint <= 0xFFFF) {
            return 3;
        }
        return 4;
    }
}
