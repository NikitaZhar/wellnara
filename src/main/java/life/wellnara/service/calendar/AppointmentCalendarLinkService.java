package life.wellnara.service.calendar;

import life.wellnara.dto.AppointmentCalendarLinks;
import life.wellnara.model.Appointment;
import life.wellnara.model.AppointmentStatus;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.AppointmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Produces per-appointment one-click "add to calendar" links for the
 * appointments page, keyed by appointment id.
 *
 * <p>Covers only scheduled appointments — the rows that carry an "Add to
 * calendar" action. Each event is rendered from the viewer's own perspective
 * and reuses {@link CalendarEventFactory}, so titles, times and description
 * match the ICS download and the subscription feed exactly.
 */
@Service
public class AppointmentCalendarLinkService {

    private final AppointmentRepository appointmentRepository;
    private final CalendarEventFactory calendarEventFactory;
    private final CalendarLinkBuilder calendarLinkBuilder;

    /**
     * Creates the appointment calendar link service.
     *
     * @param appointmentRepository repository for the viewer's appointments
     * @param calendarEventFactory  factory turning an appointment into a calendar event
     * @param calendarLinkBuilder   builder of the provider-specific add links
     */
    public AppointmentCalendarLinkService(AppointmentRepository appointmentRepository,
                                          CalendarEventFactory calendarEventFactory,
                                          CalendarLinkBuilder calendarLinkBuilder) {
        this.appointmentRepository = appointmentRepository;
        this.calendarEventFactory = calendarEventFactory;
        this.calendarLinkBuilder = calendarLinkBuilder;
    }

    /**
     * Returns the add-to-calendar links for the viewer's scheduled appointments,
     * keyed by appointment id.
     *
     * @param user authenticated viewer (provider or client)
     * @return map of appointment id to its add links; empty if none are scheduled
     */
    @Transactional(readOnly = true)
    public Map<Long, AppointmentCalendarLinks> scheduledLinksFor(User user) {
        CalendarAudience audience = user.getRole() == UserRole.PROVIDER
                ? CalendarAudience.PROVIDER
                : CalendarAudience.CLIENT;

        Map<Long, AppointmentCalendarLinks> links = new LinkedHashMap<>();
        for (Appointment appointment : scheduledAppointmentsOf(user, audience)) {
            CalendarEvent event = calendarEventFactory.create(appointment, audience);
            links.put(appointment.getId(), new AppointmentCalendarLinks(
                    calendarLinkBuilder.googleTemplateUrl(event),
                    calendarLinkBuilder.outlookComposeUrl(event)));
        }

        return links;
    }

    private List<Appointment> scheduledAppointmentsOf(User user, CalendarAudience audience) {
        return audience == CalendarAudience.PROVIDER
                ? appointmentRepository.findAllByProviderAndStatusInOrderByStartDateTimeUtcAsc(
                        user, List.of(AppointmentStatus.SCHEDULED))
                : appointmentRepository.findAllByClientAndStatusOrderByStartDateTimeUtcAsc(
                        user, AppointmentStatus.SCHEDULED);
    }
}
