package life.wellnara.service.email;

import life.wellnara.config.LocalizationConfig;
import life.wellnara.model.Appointment;
import life.wellnara.model.User;
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
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;

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
 * <p>Each recipient's email is written in their own preferred language
 * ({@link User#getLanguage()}, falling back to the application default when
 * unset). The date and time are rendered in the provider's time zone with an
 * explicit zone label, so the moment is unambiguous for both participants, but
 * the month and day names follow the recipient's language.
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
    private final MessageSource messageSource;

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
     * @param messageSource            resolver of localized email text
     */
    public AppointmentNotificationService(AppointmentRepository appointmentRepository,
                                          UserProfileService userProfileService,
                                          ApplicationTimeService applicationTimeService,
                                          AppointmentTimeFormatter timeFormatter,
                                          CalendarEventFactory calendarEventFactory,
                                          ICalendarSerializer iCalendarSerializer,
                                          EmailService emailService,
                                          MessageSource messageSource) {
        this.appointmentRepository = appointmentRepository;
        this.userProfileService = userProfileService;
        this.applicationTimeService = applicationTimeService;
        this.timeFormatter = timeFormatter;
        this.calendarEventFactory = calendarEventFactory;
        this.iCalendarSerializer = iCalendarSerializer;
        this.emailService = emailService;
        this.messageSource = messageSource;
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
            User provider = appointment.getProvider();
            User client = appointment.getClient();

            ZoneId providerZone = applicationTimeService.resolveProviderCalendarZone(provider);
            Instant startInstant = appointment.getStartDateTimeUtc().toInstant(ZoneOffset.UTC);
            String sessionName = appointment.getOffering().getName();
            String providerName = userProfileService.resolveDisplayName(provider);
            String clientName = userProfileService.resolveDisplayName(client);

            Locale providerLocale = localeOf(provider);
            send(provider.getEmail(),
                    notification,
                    providerLocale,
                    body(notification, providerLocale, sessionName,
                            timeFormatter.format(startInstant, providerZone, providerLocale),
                            msg("email.appointment.counterparty.client", providerLocale, clientName)),
                    attachment(appointment, CalendarAudience.PROVIDER, notification.method()),
                    appointmentId);

            Locale clientLocale = localeOf(client);
            send(client.getEmail(),
                    notification,
                    clientLocale,
                    body(notification, clientLocale, sessionName,
                            timeFormatter.format(startInstant, providerZone, clientLocale),
                            msg("email.appointment.counterparty.provider", clientLocale, providerName)),
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
                      Locale locale,
                      String body,
                      CalendarAttachment attachment,
                      long appointmentId) {
        try {
            emailService.sendCalendarEmail(recipient, msg(notification.subjectKey(), locale), body, attachment);
        } catch (RuntimeException exception) {
            log.warn("Could not send {} email for appointment {} to {}",
                    notification, appointmentId, recipient, exception);
        }
    }

    private String body(Notification notification,
                        Locale locale,
                        String sessionName,
                        String when,
                        String counterpartyLine) {
        return msg(notification.headlineKey(), locale) + "\n\n"
                + msg("email.appointment.session", locale, sessionName) + "\n"
                + msg("email.appointment.when", locale, when) + "\n"
                + counterpartyLine + "\n";
    }

    private CalendarAttachment attachment(Appointment appointment,
                                          CalendarAudience audience,
                                          CalendarMethod method) {
        CalendarEvent event = calendarEventFactory.create(appointment, audience);
        String calendar = iCalendarSerializer.serialize(event, method);

        return new CalendarAttachment("appointment-" + appointment.getId() + ".ics", method, calendar);
    }

    private Locale localeOf(User user) {
        String language = user.getLanguage();

        return language == null || language.isBlank()
                ? LocalizationConfig.SUPPORTED_LOCALES.get(0)
                : Locale.forLanguageTag(language);
    }

    private String msg(String key, Locale locale, Object... args) {
        return messageSource.getMessage(key, args, locale);
    }

    /**
     * Message keys and iCalendar method for each kind of appointment notification.
     */
    private enum Notification {

        SCHEDULED("email.appointment.subject.scheduled",
                "email.appointment.headline.scheduled",
                CalendarMethod.REQUEST),

        CANCELLED("email.appointment.subject.cancelled",
                "email.appointment.headline.cancelled",
                CalendarMethod.CANCEL);

        private final String subjectKey;
        private final String headlineKey;
        private final CalendarMethod method;

        Notification(String subjectKey, String headlineKey, CalendarMethod method) {
            this.subjectKey = subjectKey;
            this.headlineKey = headlineKey;
            this.method = method;
        }

        private String subjectKey() {
            return subjectKey;
        }

        private String headlineKey() {
            return headlineKey;
        }

        private CalendarMethod method() {
            return method;
        }
    }
}
