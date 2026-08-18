package life.wellnara.service.calendar;

import life.wellnara.model.Appointment;
import life.wellnara.model.AppointmentStatus;
import life.wellnara.model.User;
import life.wellnara.repository.AppointmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

/**
 * Produces a per-appointment iCalendar download for the "Add to calendar" action.
 *
 * <p>Only a scheduled appointment is downloadable, and only by one of its two
 * participants; any other case returns an empty result so the controller can
 * answer {@code 404} without revealing whether the appointment exists.
 */
@Service
public class AppointmentCalendarService {

    private final AppointmentRepository appointmentRepository;
    private final CalendarEventFactory calendarEventFactory;
    private final ICalendarSerializer iCalendarSerializer;

    /**
     * Creates the appointment calendar service.
     *
     * @param appointmentRepository repository for appointments
     * @param calendarEventFactory  factory for the calendar event
     * @param iCalendarSerializer   serializer for the calendar document
     */
    public AppointmentCalendarService(AppointmentRepository appointmentRepository,
                                      CalendarEventFactory calendarEventFactory,
                                      ICalendarSerializer iCalendarSerializer) {
        this.appointmentRepository = appointmentRepository;
        this.calendarEventFactory = calendarEventFactory;
        this.iCalendarSerializer = iCalendarSerializer;
    }

    /**
     * Builds the calendar download of a scheduled appointment for a participant.
     *
     * @param user          currently authenticated user requesting the download
     * @param appointmentId appointment identifier
     * @return the download, or empty if the appointment is not scheduled or the
     *         user is not one of its participants
     */
    @Transactional(readOnly = true)
    public Optional<CalendarDownload> downloadFor(User user, long appointmentId) {
        Objects.requireNonNull(user, "user is required");

        return appointmentRepository.findById(appointmentId)
                .filter(appointment -> appointment.getStatus() == AppointmentStatus.SCHEDULED)
                .flatMap(appointment -> audienceOf(user, appointment)
                        .map(audience -> toDownload(appointment, audience)));
    }

    private Optional<CalendarAudience> audienceOf(User user, Appointment appointment) {
        if (isSameUser(user, appointment.getProvider())) {
            return Optional.of(CalendarAudience.PROVIDER);
        }
        if (isSameUser(user, appointment.getClient())) {
            return Optional.of(CalendarAudience.CLIENT);
        }
        return Optional.empty();
    }

    private boolean isSameUser(User user, User participant) {
        return participant != null && participant.getId().equals(user.getId());
    }

    private CalendarDownload toDownload(Appointment appointment, CalendarAudience audience) {
        CalendarEvent event = calendarEventFactory.create(appointment, audience);
        String calendar = iCalendarSerializer.serialize(event, CalendarMethod.PUBLISH);

        return new CalendarDownload("appointment-" + appointment.getId() + ".ics", calendar);
    }
}
