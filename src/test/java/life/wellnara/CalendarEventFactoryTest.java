package life.wellnara;

import life.wellnara.model.Appointment;
import life.wellnara.model.AppointmentStatus;
import life.wellnara.model.Offering;
import life.wellnara.model.User;
import life.wellnara.service.calendar.CalendarAudience;
import life.wellnara.service.calendar.CalendarEvent;
import life.wellnara.service.calendar.CalendarEventFactory;
import life.wellnara.service.calendar.CalendarEventStatus;
import life.wellnara.service.calendar.CalendarLinkBuilder;
import life.wellnara.service.time.ApplicationTimeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for mapping an appointment to a calendar event.
 *
 * <p>The appointment, offering and time service are mocked so the mapping logic
 * (identifier scheme, duration, status, timestamp, description) is verified in
 * isolation, without a Spring context or a database.
 */
class CalendarEventFactoryTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 17, 13, 0);
    private static final LocalDateTime GENERATED_AT = LocalDateTime.of(2026, 8, 1, 9, 30);

    private final CalendarLinkBuilder linkBuilder =
            new CalendarLinkBuilder("https://app.wellnara.life");
    private final ApplicationTimeService applicationTimeService = mock(ApplicationTimeService.class);
    private final CalendarEventFactory factory =
            new CalendarEventFactory(linkBuilder, applicationTimeService);

    @Test
    @DisplayName("A scheduled appointment maps to a confirmed event with stable UID and derived times")
    void mapsScheduledAppointment() {
        when(applicationTimeService.currentUtcDateTime()).thenReturn(GENERATED_AT);
        Appointment appointment = appointment(42L, 7L, AppointmentStatus.SCHEDULED, 90);

        CalendarEvent event = factory.create(appointment, CalendarAudience.CLIENT);

        assertThat(event.uid()).isEqualTo("appointment-42@wellnara.life");
        assertThat(event.sequence()).isEqualTo(7L);
        assertThat(event.timestamp()).isEqualTo(GENERATED_AT.toInstant(ZoneOffset.UTC));
        assertThat(event.start()).isEqualTo(START.toInstant(ZoneOffset.UTC));
        assertThat(event.end()).isEqualTo(START.plusMinutes(90).toInstant(ZoneOffset.UTC));
        assertThat(event.summary()).isEqualTo("Deep Tissue Massage");
        assertThat(event.status()).isEqualTo(CalendarEventStatus.CONFIRMED);
        assertThat(event.description())
                .contains("https://app.wellnara.life/client/appointments");
    }

    @Test
    @DisplayName("A cancelled appointment maps to a cancelled event")
    void mapsCancelledAppointment() {
        when(applicationTimeService.currentUtcDateTime()).thenReturn(GENERATED_AT);
        Appointment appointment = appointment(5L, 2L, AppointmentStatus.CANCELLED, 60);

        CalendarEvent event = factory.create(appointment, CalendarAudience.PROVIDER);

        assertThat(event.status()).isEqualTo(CalendarEventStatus.CANCELLED);
        assertThat(event.description())
                .contains("https://app.wellnara.life/provider/appointments");
    }

    @Test
    @DisplayName("A null version yields sequence zero")
    void nullVersionYieldsZeroSequence() {
        when(applicationTimeService.currentUtcDateTime()).thenReturn(GENERATED_AT);
        Appointment appointment = appointment(1L, null, AppointmentStatus.SCHEDULED, 30);

        CalendarEvent event = factory.create(appointment, CalendarAudience.CLIENT);

        assertThat(event.sequence()).isZero();
    }

    @Test
    @DisplayName("Provider event covers the prep/wrap buffers and notes the real session time")
    void providerEventCoversBuffersWithNote() {
        when(applicationTimeService.currentUtcDateTime()).thenReturn(GENERATED_AT);
        Appointment appointment = appointmentWithBuffers(90, 15, 15, ZoneOffset.UTC);

        CalendarEvent event = factory.create(appointment, CalendarAudience.PROVIDER);

        // Block spans [start - prep, end + wrap].
        assertThat(event.start()).isEqualTo(START.minusMinutes(15).toInstant(ZoneOffset.UTC));
        assertThat(event.end()).isEqualTo(START.plusMinutes(90).plusMinutes(15).toInstant(ZoneOffset.UTC));
        assertThat(event.description())
                .contains("Session 13:00–14:30")
                .contains("Prep 15 min before")
                .contains("Wrap-up 15 min after")
                .contains("https://app.wellnara.life/provider/appointments");
    }

    @Test
    @DisplayName("Client event ignores the buffers and stays the session alone")
    void clientEventIgnoresBuffers() {
        when(applicationTimeService.currentUtcDateTime()).thenReturn(GENERATED_AT);
        Appointment appointment = appointmentWithBuffers(90, 15, 15, ZoneOffset.UTC);

        CalendarEvent event = factory.create(appointment, CalendarAudience.CLIENT);

        assertThat(event.start()).isEqualTo(START.toInstant(ZoneOffset.UTC));
        assertThat(event.end()).isEqualTo(START.plusMinutes(90).toInstant(ZoneOffset.UTC));
        assertThat(event.description())
                .doesNotContain("Session")
                .doesNotContain("Prep")
                .contains("https://app.wellnara.life/client/appointments");
    }

    private Appointment appointmentWithBuffers(int durationMinutes,
                                               int prepMinutes,
                                               int wrapMinutes,
                                               ZoneId providerZone) {
        Offering offering = mock(Offering.class);
        when(offering.getName()).thenReturn("Deep Tissue Massage");
        when(offering.getDurationMinutes()).thenReturn(durationMinutes);
        when(offering.getPrepMinutes()).thenReturn(prepMinutes);
        when(offering.getWrapMinutes()).thenReturn(wrapMinutes);

        User provider = mock(User.class);

        Appointment appointment = mock(Appointment.class);
        when(appointment.getId()).thenReturn(77L);
        when(appointment.getVersion()).thenReturn(1L);
        when(appointment.getStatus()).thenReturn(AppointmentStatus.SCHEDULED);
        when(appointment.getStartDateTimeUtc()).thenReturn(START);
        when(appointment.getOffering()).thenReturn(offering);
        when(appointment.getProvider()).thenReturn(provider);
        when(applicationTimeService.resolveProviderCalendarZone(provider)).thenReturn(providerZone);

        return appointment;
    }

    private Appointment appointment(Long id, Long version, AppointmentStatus status, int durationMinutes) {
        Offering offering = mock(Offering.class);
        when(offering.getName()).thenReturn("Deep Tissue Massage");
        when(offering.getDurationMinutes()).thenReturn(durationMinutes);

        Appointment appointment = mock(Appointment.class);
        when(appointment.getId()).thenReturn(id);
        when(appointment.getVersion()).thenReturn(version);
        when(appointment.getStatus()).thenReturn(status);
        when(appointment.getStartDateTimeUtc()).thenReturn(START);
        when(appointment.getOffering()).thenReturn(offering);

        return appointment;
    }
}
