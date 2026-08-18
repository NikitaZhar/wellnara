package life.wellnara;

import life.wellnara.model.Appointment;
import life.wellnara.model.AppointmentStatus;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.AppointmentRepository;
import life.wellnara.service.calendar.CalendarAudience;
import life.wellnara.service.calendar.CalendarEvent;
import life.wellnara.service.calendar.CalendarEventFactory;
import life.wellnara.service.calendar.CalendarEventStatus;
import life.wellnara.service.calendar.CalendarFeedService;
import life.wellnara.service.calendar.CalendarMethod;
import life.wellnara.service.calendar.CalendarSubscriptionService;
import life.wellnara.service.calendar.ICalendarSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for the feed builder: audience is chosen by role, only the
 * matching query is used, and an unresolved token yields no feed.
 */
class CalendarFeedServiceTest {

    private final CalendarSubscriptionService subscriptionService = mock(CalendarSubscriptionService.class);
    private final AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);
    private final CalendarEventFactory calendarEventFactory = mock(CalendarEventFactory.class);
    private final ICalendarSerializer iCalendarSerializer = mock(ICalendarSerializer.class);
    private final CalendarFeedService service = new CalendarFeedService(
            subscriptionService, appointmentRepository, calendarEventFactory, iCalendarSerializer);

    @Test
    @DisplayName("A provider feed uses the provider query and the provider audience")
    void providerFeed() {
        User provider = user(UserRole.PROVIDER);
        Appointment appointment = mock(Appointment.class);
        when(subscriptionService.findActiveOwner("tok")).thenReturn(Optional.of(provider));
        when(appointmentRepository.findAllByProviderAndStatusInOrderByStartDateTimeUtcAsc(eq(provider), anyList()))
                .thenReturn(List.of(appointment));
        when(calendarEventFactory.create(appointment, CalendarAudience.PROVIDER)).thenReturn(event());
        when(iCalendarSerializer.serialize(anyList(), eq(CalendarMethod.PUBLISH))).thenReturn("ICS");

        assertThat(service.feedFor("tok")).contains("ICS");
        verify(calendarEventFactory).create(appointment, CalendarAudience.PROVIDER);
        verify(appointmentRepository, never())
                .findAllByClientAndStatusInOrderByStartDateTimeUtcAsc(any(), anyList());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AppointmentStatus>> statuses = ArgumentCaptor.forClass(List.class);
        verify(appointmentRepository)
                .findAllByProviderAndStatusInOrderByStartDateTimeUtcAsc(eq(provider), statuses.capture());
        assertThat(statuses.getValue()).containsExactly(
                AppointmentStatus.SCHEDULED, AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW);
    }

    @Test
    @DisplayName("A client feed uses the client query and the client audience")
    void clientFeed() {
        User client = user(UserRole.CLIENT);
        Appointment appointment = mock(Appointment.class);
        when(subscriptionService.findActiveOwner("tok")).thenReturn(Optional.of(client));
        when(appointmentRepository.findAllByClientAndStatusInOrderByStartDateTimeUtcAsc(eq(client), anyList()))
                .thenReturn(List.of(appointment));
        when(calendarEventFactory.create(appointment, CalendarAudience.CLIENT)).thenReturn(event());
        when(iCalendarSerializer.serialize(anyList(), eq(CalendarMethod.PUBLISH))).thenReturn("ICS");

        assertThat(service.feedFor("tok")).contains("ICS");
        verify(calendarEventFactory).create(appointment, CalendarAudience.CLIENT);
        verify(appointmentRepository, never())
                .findAllByProviderAndStatusInOrderByStartDateTimeUtcAsc(any(), anyList());
    }

    @Test
    @DisplayName("An unresolved token yields no feed and touches nothing")
    void unresolvedTokenYieldsNothing() {
        when(subscriptionService.findActiveOwner("bad")).thenReturn(Optional.empty());

        assertThat(service.feedFor("bad")).isEmpty();
        verifyNoInteractions(appointmentRepository, calendarEventFactory, iCalendarSerializer);
    }

    private User user(UserRole role) {
        User user = mock(User.class);
        when(user.getRole()).thenReturn(role);
        return user;
    }

    private CalendarEvent event() {
        Instant now = Instant.parse("2026-08-01T00:00:00Z");
        return new CalendarEvent("appointment-1@wellnara.life", 0L, now, now, now,
                "Session", "Description", CalendarEventStatus.CONFIRMED);
    }
}
