package life.wellnara;

import life.wellnara.model.Appointment;
import life.wellnara.model.AppointmentStatus;
import life.wellnara.model.User;
import life.wellnara.repository.AppointmentRepository;
import life.wellnara.service.calendar.AppointmentCalendarService;
import life.wellnara.service.calendar.CalendarDownload;
import life.wellnara.service.calendar.CalendarEvent;
import life.wellnara.service.calendar.CalendarEventFactory;
import life.wellnara.service.calendar.CalendarEventStatus;
import life.wellnara.service.calendar.CalendarMethod;
import life.wellnara.service.calendar.ICalendarSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for the "Add to calendar" download: only a scheduled
 * appointment is downloadable, and only by one of its two participants.
 */
class AppointmentCalendarServiceTest {

    private static final long APPOINTMENT_ID = 5L;

    private final AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);
    private final CalendarEventFactory calendarEventFactory = mock(CalendarEventFactory.class);
    private final ICalendarSerializer iCalendarSerializer = mock(ICalendarSerializer.class);
    private final AppointmentCalendarService service =
            new AppointmentCalendarService(appointmentRepository, calendarEventFactory, iCalendarSerializer);

    @Test
    @DisplayName("The provider of a scheduled appointment gets the download")
    void providerGetsDownload() {
        when(calendarEventFactory.create(any(), any())).thenReturn(event());
        when(iCalendarSerializer.serialize(any(CalendarEvent.class), eq(CalendarMethod.PUBLISH)))
                .thenReturn("ICS");
        given(scheduledAppointment(1L, 2L));

        Optional<CalendarDownload> download = service.downloadFor(user(1L), APPOINTMENT_ID);

        assertThat(download).isPresent();
        assertThat(download.get().fileName()).isEqualTo("appointment-5.ics");
        assertThat(download.get().content()).isEqualTo("ICS");
    }

    @Test
    @DisplayName("The client of a scheduled appointment gets the download")
    void clientGetsDownload() {
        when(calendarEventFactory.create(any(), any())).thenReturn(event());
        when(iCalendarSerializer.serialize(any(CalendarEvent.class), eq(CalendarMethod.PUBLISH)))
                .thenReturn("ICS");
        given(scheduledAppointment(1L, 2L));

        assertThat(service.downloadFor(user(2L), APPOINTMENT_ID)).isPresent();
    }

    @Test
    @DisplayName("A non-participant gets nothing and no calendar is built")
    void strangerGetsNothing() {
        given(scheduledAppointment(1L, 2L));

        assertThat(service.downloadFor(user(3L), APPOINTMENT_ID)).isEmpty();
        verifyNoInteractions(calendarEventFactory, iCalendarSerializer);
    }

    @Test
    @DisplayName("A non-scheduled appointment is not downloadable")
    void nonScheduledIsNotDownloadable() {
        Appointment appointment = mock(Appointment.class);
        when(appointment.getStatus()).thenReturn(AppointmentStatus.REQUESTED);
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

        assertThat(service.downloadFor(user(1L), APPOINTMENT_ID)).isEmpty();
        verify(calendarEventFactory, never()).create(any(), any());
    }

    @Test
    @DisplayName("A missing appointment yields nothing")
    void missingAppointmentYieldsNothing() {
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.empty());

        assertThat(service.downloadFor(user(1L), APPOINTMENT_ID)).isEmpty();
    }

    private void given(Appointment appointment) {
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));
    }

    private Appointment scheduledAppointment(long providerId, long clientId) {
        User provider = user(providerId);
        User client = user(clientId);

        Appointment appointment = mock(Appointment.class);
        when(appointment.getStatus()).thenReturn(AppointmentStatus.SCHEDULED);
        when(appointment.getId()).thenReturn(APPOINTMENT_ID);
        when(appointment.getProvider()).thenReturn(provider);
        when(appointment.getClient()).thenReturn(client);
        return appointment;
    }

    private User user(long id) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        return user;
    }

    private CalendarEvent event() {
        Instant now = Instant.parse("2026-08-01T00:00:00Z");
        return new CalendarEvent("appointment-5@wellnara.life", 0L, now, now, now,
                "Session", "Description", CalendarEventStatus.CONFIRMED);
    }
}
