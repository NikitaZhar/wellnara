package life.wellnara.service.email;

import life.wellnara.model.Appointment;
import life.wellnara.repository.AppointmentRepository;
import life.wellnara.service.UserProfileService;
import life.wellnara.service.calendar.CalendarAudience;
import life.wellnara.service.calendar.CalendarEvent;
import life.wellnara.service.calendar.CalendarEventFactory;
import life.wellnara.service.calendar.CalendarMethod;
import life.wellnara.service.calendar.ICalendarSerializer;
import life.wellnara.service.time.ApplicationTimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Builds and sends appointment confirmation and cancellation emails to both
 * participants, each with an iCalendar attachment mirroring the appointment.
 *
 * <p>Invoked from a post-commit listener with only the appointment id, so it
 * re-loads the appointment in its own read-only transaction.
 *
 * <p>Delivery is best-effort: each recipient is sent independently and a mail
 * failure is caught and logged here, so it never blocks the other recipient and
 * never propagates out of the post-commit callback into the already-committed
 * appointment transaction.
 *
 * <p>Each recipient sees the session name, the date and time in the provider's
 * time zone with an explicit zone label, and the other participant's name.
 */
@Service
public class AppointmentNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentNotificationService.class);

    private final AppointmentRepository appointmentRepository;
    private final UserProfileService userProfileService;
    private final ApplicationTimeService applicationTimeService;
    private final AppointmentTimeFormatter timeFormatter;
    private final CalendarEventFactory calendarEventFactory;
    private final ICalendarSerializer iCalendarSerializer;
    private final EmailService emailService;

    /**
     * Creates the appointment notification service.
     *
     * @param appointmentRepository    repository for re-loading the appointment
     * @param userProfileService       resolver of participant display names
     * @param applicationTimeService   resolver of the provider calendar time zone
     * @param timeFormatter            formatter for the human-readable start time
     * @param calendarEventFactory     factory for the calendar event
     * @param iCalendarSerializer      serializer for the calendar attachment
     * @param emailService             low-level email delivery
     */
    public AppointmentNotificationService(AppointmentRepository appointmentRepository,
                                          UserProfileService userProfileService,
                                          ApplicationTimeService applicationTimeService,
                                          AppointmentTimeFormatter timeFormatter,
                                          CalendarEventFactory calendarEventFactory,
                                          ICalendarSerializer iCalendarSerializer,
                                          EmailService emailService) {
        this.appointmentRepository = appointmentRepository;
        this.userProfileService = userProfileService;
        this.applicationTimeService = applicationTimeService;
        this.timeFormatter = timeFormatter;
        this.calendarEventFactory = calendarEventFactory;
        this.iCalendarSerializer = iCalendarSerializer;
        this.emailService = emailService;
    }

    /**
     * Notifies both participants that the appointment is confirmed.
     *
     * @param appointmentId identifier of the scheduled appointment
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void notifyScheduled(long appointmentId) {
        notify(appointmentId, Notification.SCHEDULED);
    }

    /**
     * Notifies both participants that the appointment is cancelled.
     *
     * @param appointmentId identifier of the cancelled appointment
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void notifyCancelled(long appointmentId) {
        notify(appointmentId, Notification.CANCELLED);
    }

    private void notify(long appointmentId, Notification notification) {
        appointmentRepository.findById(appointmentId).ifPresent(appointment -> {
            Details details = detailsOf(appointment);

            send(appointment.getProvider().getEmail(),
                    notification,
                    body(notification, details, "Client: " + details.clientName()),
                    attachment(appointment, CalendarAudience.PROVIDER, notification.method()),
                    appointmentId);

            send(appointment.getClient().getEmail(),
                    notification,
                    body(notification, details, "Provider: " + details.providerName()),
                    attachment(appointment, CalendarAudience.CLIENT, notification.method()),
                    appointmentId);
        });
    }

    /**
     * Sends one notification email, keeping delivery best-effort: a failure is
     * logged and never propagated, so the other recipient is still attempted and
     * the committed appointment transaction is never affected.
     */
    private void send(String recipient,
                      Notification notification,
                      String body,
                      CalendarAttachment attachment,
                      long appointmentId) {
        try {
            emailService.sendCalendarEmail(recipient, notification.subject(), body, attachment);
        } catch (RuntimeException exception) {
            log.warn("Could not send {} email for appointment {} to {}",
                    notification, appointmentId, recipient, exception);
        }
    }

    private Details detailsOf(Appointment appointment) {
        ZoneId providerZone = applicationTimeService.resolveProviderCalendarZone(appointment.getProvider());
        String when = timeFormatter.format(
                appointment.getStartDateTimeUtc().toInstant(ZoneOffset.UTC), providerZone);

        return new Details(
                appointment.getOffering().getName(),
                when,
                userProfileService.resolveDisplayName(appointment.getProvider()),
                userProfileService.resolveDisplayName(appointment.getClient()));
    }

    private String body(Notification notification, Details details, String counterpartyLine) {
        return """
                %s

                Session: %s
                When: %s
                %s
                """.formatted(notification.headline(), details.sessionName(), details.when(), counterpartyLine);
    }

    private CalendarAttachment attachment(Appointment appointment,
                                          CalendarAudience audience,
                                          CalendarMethod method) {
        CalendarEvent event = calendarEventFactory.create(appointment, audience);
        String calendar = iCalendarSerializer.serialize(event, method);

        return new CalendarAttachment("appointment-" + appointment.getId() + ".ics", method, calendar);
    }

    /**
     * Wording and iCalendar method for each kind of appointment notification.
     */
    private enum Notification {

        SCHEDULED("Wellnara — appointment confirmed",
                "Your appointment is confirmed.",
                CalendarMethod.REQUEST),

        CANCELLED("Wellnara — appointment cancelled",
                "Your appointment has been cancelled.",
                CalendarMethod.CANCEL);

        private final String subject;
        private final String headline;
        private final CalendarMethod method;

        Notification(String subject, String headline, CalendarMethod method) {
            this.subject = subject;
            this.headline = headline;
            this.method = method;
        }

        private String subject() {
            return subject;
        }

        private String headline() {
            return headline;
        }

        private CalendarMethod method() {
            return method;
        }
    }

    /**
     * Resolved, transaction-free details shared by both participants' emails.
     */
    private record Details(String sessionName, String when, String providerName, String clientName) {
    }
}
