package life.wellnara.service.calendar;

import life.wellnara.model.Appointment;
import life.wellnara.model.AppointmentStatus;
import life.wellnara.service.time.ApplicationTimeService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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

        int prepMinutes = appointment.getOffering().getPrepMinutes();
        int wrapMinutes = appointment.getOffering().getWrapMinutes();
        boolean forProvider = audience == CalendarAudience.PROVIDER;

        Instant sessionStart = appointment.getStartDateTimeUtc().toInstant(ZoneOffset.UTC);
        Instant sessionEnd = sessionStart
                .plus(Duration.ofMinutes(appointment.getOffering().getDurationMinutes()));

        // The provider's calendar block also covers their prep/wrap buffers; the
        // client's event is the booked session alone.
        Instant start = forProvider ? sessionStart.minus(Duration.ofMinutes(prepMinutes)) : sessionStart;
        Instant end = forProvider ? sessionEnd.plus(Duration.ofMinutes(wrapMinutes)) : sessionEnd;

        return new CalendarEvent(
                uid(appointment),
                sequence(appointment),
                applicationTimeService.currentUtcDateTime().toInstant(ZoneOffset.UTC),
                start,
                end,
                appointment.getOffering().getName(),
                description(appointment, audience, forProvider, prepMinutes, wrapMinutes),
                status(appointment.getStatus()));
    }

    private String uid(Appointment appointment) {
        return UID_PREFIX + appointment.getId() + UID_DOMAIN;
    }

    private long sequence(Appointment appointment) {
        Long version = appointment.getVersion();

        return version == null ? 0L : version;
    }

    private String description(Appointment appointment,
                              CalendarAudience audience,
                              boolean forProvider,
                              int prepMinutes,
                              int wrapMinutes) {
        String manageLine = "Manage this appointment in Wellnara: " + linkBuilder.appointmentsLink(audience);

        if (!forProvider || (prepMinutes == 0 && wrapMinutes == 0)) {
            return manageLine;
        }

        return bufferNote(appointment, prepMinutes, wrapMinutes) + "\n\n" + manageLine;
    }

    /**
     * Human-readable note placed on the provider's padded calendar block that
     * spells out the actual session time and the prep/wrap buffers around it.
     *
     * @param appointment appointment being mirrored
     * @param prepMinutes preparation buffer before the session
     * @param wrapMinutes wrap-up buffer after the session
     * @return description note in the provider's calendar timezone
     */
    private String bufferNote(Appointment appointment, int prepMinutes, int wrapMinutes) {
        ZoneId providerZone = applicationTimeService.resolveProviderCalendarZone(appointment.getProvider());
        DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm");

        LocalDateTime sessionStartLocal = appointment.getStartDateTimeUtc()
                .atZone(ZoneOffset.UTC)
                .withZoneSameInstant(providerZone)
                .toLocalDateTime();
        LocalDateTime sessionEndLocal = sessionStartLocal
                .plusMinutes(appointment.getOffering().getDurationMinutes());

        StringBuilder note = new StringBuilder()
                .append("Session ")
                .append(sessionStartLocal.format(timeFormat))
                .append("–")
                .append(sessionEndLocal.format(timeFormat))
                .append('.');

        if (prepMinutes > 0) {
            note.append(" Prep ").append(prepMinutes).append(" min before.");
        }
        if (wrapMinutes > 0) {
            note.append(" Wrap-up ").append(wrapMinutes).append(" min after.");
        }

        return note.toString();
    }

    private CalendarEventStatus status(AppointmentStatus appointmentStatus) {
        return appointmentStatus == AppointmentStatus.CANCELLED
                ? CalendarEventStatus.CANCELLED
                : CalendarEventStatus.CONFIRMED;
    }
}
