package life.wellnara.service.calendar;

import life.wellnara.model.Appointment;
import life.wellnara.model.AppointmentStatus;
import life.wellnara.service.time.ApplicationTimeService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Maps an {@link Appointment} to a calendar-neutral {@link CalendarEvent}.
 *
 * <p>This is the single place that knows how an appointment becomes a calendar
 * event, so the identifier scheme, duration and status mapping live in one
 * class. Serialization is delegated to {@link ICalendarSerializer} and link
 * construction to {@link CalendarLinkBuilder}, keeping this factory focused on
 * the domain-to-calendar translation.
 */
@Component
public class CalendarEventFactory {

    private static final String UID_PREFIX = "appointment-";
    private static final String UID_DOMAIN = "@wellnara.life";

    private final CalendarLinkBuilder linkBuilder;
    private final ApplicationTimeService applicationTimeService;

    /**
     * Creates the calendar event factory.
     *
     * @param linkBuilder            builder for the manage link embedded in the event
     * @param applicationTimeService source of the generation timestamp
     */
    public CalendarEventFactory(CalendarLinkBuilder linkBuilder,
                                ApplicationTimeService applicationTimeService) {
        this.linkBuilder = linkBuilder;
        this.applicationTimeService = applicationTimeService;
    }

    /**
     * Builds the calendar event mirroring the given appointment for one audience.
     *
     * <p>The event uses a stable UID derived from the appointment id and a
     * sequence derived from the appointment's optimistic-lock version, so an
     * update or cancellation of the same appointment supersedes the previous
     * payload in the recipient's calendar.
     *
     * @param appointment appointment to mirror
     * @param audience    participant whose calendar the event is intended for
     * @return calendar event describing the appointment
     */
    public CalendarEvent create(Appointment appointment, CalendarAudience audience) {
        Objects.requireNonNull(appointment, "appointment is required");
        Objects.requireNonNull(audience, "audience is required");

        Instant start = appointment.getStartDateTimeUtc().toInstant(ZoneOffset.UTC);
        Instant end = start.plus(Duration.ofMinutes(appointment.getOffering().getDurationMinutes()));

        return new CalendarEvent(
                uid(appointment),
                sequence(appointment),
                applicationTimeService.currentUtcDateTime().toInstant(ZoneOffset.UTC),
                start,
                end,
                appointment.getOffering().getName(),
                description(audience),
                status(appointment.getStatus()));
    }

    private String uid(Appointment appointment) {
        return UID_PREFIX + appointment.getId() + UID_DOMAIN;
    }

    private long sequence(Appointment appointment) {
        Long version = appointment.getVersion();

        return version == null ? 0L : version;
    }

    private String description(CalendarAudience audience) {
        return "Manage this appointment in Wellnara: " + linkBuilder.appointmentsLink(audience);
    }

    private CalendarEventStatus status(AppointmentStatus appointmentStatus) {
        return appointmentStatus == AppointmentStatus.CANCELLED
                ? CalendarEventStatus.CANCELLED
                : CalendarEventStatus.CONFIRMED;
    }
}
