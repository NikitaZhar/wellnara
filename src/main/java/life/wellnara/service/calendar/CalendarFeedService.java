package life.wellnara.service.calendar;

import life.wellnara.model.Appointment;
import life.wellnara.model.AppointmentStatus;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.AppointmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Builds the read-only ICS feed served for a personal subscription token.
 *
 * <p>The feed mirrors the subscriber's real sessions — scheduled, completed and
 * no-show — as a published calendar; pending requests and cancelled appointments
 * are excluded, so a cancellation removes the event on the next feed refresh.
 * Each event is rendered from the subscriber's own perspective.
 */
@Service
public class CalendarFeedService {

    private static final List<AppointmentStatus> FEED_STATUSES = List.of(
            AppointmentStatus.SCHEDULED,
            AppointmentStatus.COMPLETED,
            AppointmentStatus.NO_SHOW);

    private final CalendarSubscriptionService subscriptionService;
    private final AppointmentRepository appointmentRepository;
    private final CalendarEventFactory calendarEventFactory;
    private final ICalendarSerializer iCalendarSerializer;

    /**
     * Creates the calendar feed service.
     *
     * @param subscriptionService  resolver of an active token to its owner
     * @param appointmentRepository repository for the owner's appointments
     * @param calendarEventFactory  factory for calendar events
     * @param iCalendarSerializer   serializer for the calendar document
     */
    public CalendarFeedService(CalendarSubscriptionService subscriptionService,
                               AppointmentRepository appointmentRepository,
                               CalendarEventFactory calendarEventFactory,
                               ICalendarSerializer iCalendarSerializer) {
        this.subscriptionService = subscriptionService;
        this.appointmentRepository = appointmentRepository;
        this.calendarEventFactory = calendarEventFactory;
        this.iCalendarSerializer = iCalendarSerializer;
    }

    /**
     * Builds the ICS feed for the given token.
     *
     * @param token feed token
     * @return the serialized {@code VCALENDAR}, or empty if the token is unknown
     *         or the feed is disabled
     */
    @Transactional(readOnly = true)
    public Optional<String> feedFor(String token) {
        return subscriptionService.findActiveOwner(token).map(this::buildFeed);
    }

    private String buildFeed(User owner) {
        CalendarAudience audience = audienceOf(owner);

        List<CalendarEvent> events = appointmentsOf(owner, audience).stream()
                .map(appointment -> calendarEventFactory.create(appointment, audience))
                .toList();

        return iCalendarSerializer.serialize(events, CalendarMethod.PUBLISH);
    }

    private CalendarAudience audienceOf(User owner) {
        return owner.getRole() == UserRole.PROVIDER ? CalendarAudience.PROVIDER : CalendarAudience.CLIENT;
    }

    private List<Appointment> appointmentsOf(User owner, CalendarAudience audience) {
        return audience == CalendarAudience.PROVIDER
                ? appointmentRepository.findAllByProviderAndStatusInOrderByStartDateTimeUtcAsc(owner, FEED_STATUSES)
                : appointmentRepository.findAllByClientAndStatusInOrderByStartDateTimeUtcAsc(owner, FEED_STATUSES);
    }
}
