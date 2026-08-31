package life.wellnara;

import life.wellnara.model.Appointment;
import life.wellnara.model.Offering;
import life.wellnara.model.User;
import life.wellnara.repository.AppointmentRepository;
import life.wellnara.service.UserProfileService;
import life.wellnara.service.calendar.CalendarAudience;
import life.wellnara.service.calendar.CalendarEvent;
import life.wellnara.service.calendar.CalendarEventFactory;
import life.wellnara.service.calendar.CalendarEventStatus;
import life.wellnara.service.calendar.CalendarMethod;
import life.wellnara.service.calendar.ICalendarSerializer;
import life.wellnara.service.email.AppointmentNotificationService;
import life.wellnara.service.email.AppointmentTimeFormatter;
import life.wellnara.service.email.CalendarAttachment;
import life.wellnara.service.email.EmailService;
import life.wellnara.service.time.ApplicationTimeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for assembling the confirmation and cancellation emails: each
 * participant receives the session name, the start time and the other party's
 * name, plus an attachment with the matching iCalendar method.
 */
class AppointmentNotificationServiceTest {

    private static final long APPOINTMENT_ID = 10L;

    private final AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);
    private final UserProfileService userProfileService = mock(UserProfileService.class);
    private final ApplicationTimeService applicationTimeService = mock(ApplicationTimeService.class);
    private final AppointmentTimeFormatter timeFormatter = mock(AppointmentTimeFormatter.class);
    private final CalendarEventFactory calendarEventFactory = mock(CalendarEventFactory.class);
    private final ICalendarSerializer iCalendarSerializer = mock(ICalendarSerializer.class);
    private final EmailService emailService = mock(EmailService.class);
    private final MessageSource messageSource = messageSource();

    private final AppointmentNotificationService service = new AppointmentNotificationService(
            appointmentRepository, userProfileService, applicationTimeService, timeFormatter,
            calendarEventFactory, iCalendarSerializer, emailService, messageSource);

    private static MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        return source;
    }

    @Test
    @DisplayName("Scheduling emails both participants with details and a REQUEST attachment")
    void notifiesBothOnScheduled() {
        User provider = user("provider@wellnara.life");
        User client = user("client@wellnara.life");
        givenAppointment(provider, client);
        when(iCalendarSerializer.serialize(any(CalendarEvent.class), eq(CalendarMethod.REQUEST)))
                .thenReturn("ICS");

        service.notifyScheduled(APPOINTMENT_ID);

        ArgumentCaptor<String> recipient = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<CalendarAttachment> attachment = ArgumentCaptor.forClass(CalendarAttachment.class);
        verify(emailService, org.mockito.Mockito.times(2))
                .sendCalendarEmail(recipient.capture(), subject.capture(), body.capture(), attachment.capture());

        assertThat(recipient.getAllValues())
                .containsExactly("provider@wellnara.life", "client@wellnara.life");
        assertThat(subject.getAllValues())
                .containsOnly("Wellnara — appointment confirmed");

        String providerBody = body.getAllValues().get(0);
        assertThat(providerBody).contains("Massage", "Mon, 17 Aug 2026 15:00 CEST", "Client: John Doe");

        String clientBody = body.getAllValues().get(1);
        assertThat(clientBody).contains("Provider: Dr. Smith");

        assertThat(attachment.getAllValues()).allSatisfy(a -> {
            assertThat(a.method()).isEqualTo(CalendarMethod.REQUEST);
            assertThat(a.fileName()).isEqualTo("appointment-10.ics");
        });
    }

    @Test
    @DisplayName("Cancellation emails both participants with a CANCEL attachment")
    void notifiesBothOnCancelled() {
        User provider = user("provider@wellnara.life");
        User client = user("client@wellnara.life");
        givenAppointment(provider, client);
        when(iCalendarSerializer.serialize(any(CalendarEvent.class), eq(CalendarMethod.CANCEL)))
                .thenReturn("ICS");

        service.notifyCancelled(APPOINTMENT_ID);

        ArgumentCaptor<CalendarAttachment> attachment = ArgumentCaptor.forClass(CalendarAttachment.class);
        verify(emailService, org.mockito.Mockito.times(2))
                .sendCalendarEmail(any(), eq("Wellnara — appointment cancelled"), any(), attachment.capture());

        assertThat(attachment.getAllValues()).allSatisfy(
                a -> assertThat(a.method()).isEqualTo(CalendarMethod.CANCEL));
    }

    @Test
    @DisplayName("A missing appointment sends nothing")
    void missingAppointmentSendsNothing() {
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.empty());

        service.notifyScheduled(APPOINTMENT_ID);

        org.mockito.Mockito.verifyNoInteractions(emailService);
    }

    private void givenAppointment(User provider, User client) {
        Offering offering = mock(Offering.class);
        when(offering.getName()).thenReturn("Massage");

        Appointment appointment = mock(Appointment.class);
        when(appointment.getId()).thenReturn(APPOINTMENT_ID);
        when(appointment.getProvider()).thenReturn(provider);
        when(appointment.getClient()).thenReturn(client);
        when(appointment.getOffering()).thenReturn(offering);
        when(appointment.getStartDateTimeUtc()).thenReturn(LocalDateTime.of(2026, 8, 17, 13, 0));
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

        when(applicationTimeService.resolveProviderCalendarZone(provider))
                .thenReturn(ZoneId.of("Europe/Bratislava"));
        when(timeFormatter.format(any(Instant.class), any(ZoneId.class), any(Locale.class)))
                .thenReturn("Mon, 17 Aug 2026 15:00 CEST");
        when(userProfileService.resolveDisplayName(provider)).thenReturn("Dr. Smith");
        when(userProfileService.resolveDisplayName(client)).thenReturn("John Doe");
        when(calendarEventFactory.create(any(Appointment.class), any(CalendarAudience.class)))
                .thenReturn(event());
    }

    private User user(String email) {
        User user = mock(User.class);
        when(user.getEmail()).thenReturn(email);
        when(user.getLanguage()).thenReturn("en");
        return user;
    }

    private CalendarEvent event() {
        Instant now = Instant.parse("2026-08-01T00:00:00Z");
        return new CalendarEvent("appointment-10@wellnara.life", 0L, now, now, now,
                "Massage", "Description", CalendarEventStatus.CONFIRMED);
    }
}
