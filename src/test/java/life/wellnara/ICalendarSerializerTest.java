package life.wellnara;

import life.wellnara.service.calendar.CalendarEvent;
import life.wellnara.service.calendar.CalendarEventStatus;
import life.wellnara.service.calendar.CalendarMethod;
import life.wellnara.service.calendar.ICalendarSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for RFC 5545 serialization: structure, UTC timestamps,
 * {@code TEXT} escaping and 75-octet content-line folding.
 */
class ICalendarSerializerTest {

    private static final Instant STAMP = Instant.parse("2026-08-01T09:30:00Z");
    private static final Instant START = Instant.parse("2026-08-17T13:00:00Z");
    private static final Instant END = Instant.parse("2026-08-17T14:30:00Z");

    private final ICalendarSerializer serializer = new ICalendarSerializer();

    @Test
    @DisplayName("A confirmed event serializes to a well-formed REQUEST calendar")
    void serializesConfirmedEvent() {
        String calendar = serializer.serialize(event("Massage", CalendarEventStatus.CONFIRMED),
                CalendarMethod.REQUEST);

        assertThat(lines(calendar))
                .startsWith("BEGIN:VCALENDAR")
                .contains("VERSION:2.0")
                .contains("PRODID:-//Wellnara//Appointments//EN")
                .contains("METHOD:REQUEST")
                .contains("BEGIN:VEVENT")
                .contains("UID:appointment-42@wellnara.life")
                .contains("SEQUENCE:3")
                .contains("DTSTAMP:20260801T093000Z")
                .contains("DTSTART:20260817T130000Z")
                .contains("DTEND:20260817T143000Z")
                .contains("SUMMARY:Massage")
                .contains("STATUS:CONFIRMED")
                .contains("END:VEVENT")
                .endsWith("END:VCALENDAR");
    }

    @Test
    @DisplayName("A cancellation carries METHOD:CANCEL and STATUS:CANCELLED")
    void serializesCancellation() {
        String calendar = serializer.serialize(event("Massage", CalendarEventStatus.CANCELLED),
                CalendarMethod.CANCEL);

        assertThat(lines(calendar))
                .contains("METHOD:CANCEL")
                .contains("STATUS:CANCELLED");
    }

    @Test
    @DisplayName("Every physical line ends with CRLF")
    void usesCrlfLineEndings() {
        String calendar = serializer.serialize(event("Massage", CalendarEventStatus.CONFIRMED),
                CalendarMethod.REQUEST);

        assertThat(calendar).endsWith("END:VCALENDAR\r\n");
        assertThat(calendar.replace("\r\n", "")).doesNotContain("\n");
    }

    @Test
    @DisplayName("TEXT special characters are escaped")
    void escapesTextValues() {
        String calendar = serializer.serialize(
                event("A, B; C\\D\nnext", CalendarEventStatus.CONFIRMED), CalendarMethod.REQUEST);

        assertThat(unfold(calendar)).contains("SUMMARY:A\\, B\\; C\\\\D\\nnext");
    }

    @Test
    @DisplayName("No physical line exceeds 75 octets")
    void foldsLongLines() {
        String longSummary = "x".repeat(300);

        String calendar = serializer.serialize(
                event(longSummary, CalendarEventStatus.CONFIRMED), CalendarMethod.REQUEST);

        for (String line : calendar.split("\r\n", -1)) {
            assertThat(line.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
        }
    }

    @Test
    @DisplayName("A folded line unfolds back to the original property")
    void foldedLineUnfoldsToOriginal() {
        String longSummary = "y".repeat(300);

        String calendar = serializer.serialize(
                event(longSummary, CalendarEventStatus.CONFIRMED), CalendarMethod.REQUEST);

        assertThat(unfold(calendar)).contains("SUMMARY:" + longSummary);
    }

    @Test
    @DisplayName("Multiple events serialize into one PUBLISH calendar")
    void serializesMultipleEvents() {
        List<CalendarEvent> events = List.of(
                event("First", CalendarEventStatus.CONFIRMED),
                event("Second", CalendarEventStatus.CONFIRMED));

        String calendar = serializer.serialize(events, CalendarMethod.PUBLISH);

        assertThat(lines(calendar)).filteredOn(line -> line.equals("BEGIN:VEVENT")).hasSize(2);
        assertThat(lines(calendar)).contains("METHOD:PUBLISH", "SUMMARY:First", "SUMMARY:Second");
    }

    private CalendarEvent event(String summary, CalendarEventStatus status) {
        return new CalendarEvent(
                "appointment-42@wellnara.life",
                3L,
                STAMP,
                START,
                END,
                summary,
                "Manage this appointment in Wellnara: https://app.wellnara.life/client/appointments",
                status);
    }

    private List<String> lines(String calendar) {
        String withoutTrailingBreak =
                calendar.endsWith("\r\n") ? calendar.substring(0, calendar.length() - 2) : calendar;

        return List.of(withoutTrailingBreak.split("\r\n", -1));
    }

    private String unfold(String calendar) {
        return calendar.replace("\r\n ", "");
    }
}
