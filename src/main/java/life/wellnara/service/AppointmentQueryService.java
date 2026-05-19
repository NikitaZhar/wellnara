package life.wellnara.service;

import life.wellnara.dto.AppointmentView;
import life.wellnara.model.Appointment;
import life.wellnara.model.AppointmentStatus;
import life.wellnara.model.User;
import life.wellnara.repository.AppointmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Provides read-only access to appointment data for UI rendering.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>query appointments by actor and status,</li>
 *   <li>convert results to {@link AppointmentView} via {@link AppointmentViewMapper}.</li>
 * </ul>
 *
 * <p>All methods are read-only transactions. No state mutations.
 * For slot availability use {@link AppointmentAvailabilityService}.
 */
@Service
public class AppointmentQueryService {

    private final AppointmentRepository appointmentRepository;
    private final ProviderCalendarService providerCalendarService;
    private final AppointmentViewMapper viewMapper;

    /**
     * Creates appointment query service.
     *
     * @param appointmentRepository   repository for appointments
     * @param providerCalendarService service for provider calendar operations
     * @param viewMapper              mapper for Appointment → AppointmentView conversion
     */
    public AppointmentQueryService(AppointmentRepository appointmentRepository,
                                   ProviderCalendarService providerCalendarService,
                                   AppointmentViewMapper viewMapper) {
        this.appointmentRepository = appointmentRepository;
        this.providerCalendarService = providerCalendarService;
        this.viewMapper = viewMapper;
    }

    /**
     * Returns all appointments of client.
     *
     * @param client client whose appointments are requested
     * @return client appointments ordered by date and time
     */
    @Transactional(readOnly = true)
    public List<Appointment> getAppointmentsOfClient(User client) {
        return appointmentRepository.findAllByClientOrderByStartDateTimeUtcAsc(client);
    }

    /**
     * Returns all appointments of provider.
     *
     * @param provider provider whose appointments are requested
     * @return provider appointments ordered by date and time
     */
    @Transactional(readOnly = true)
    public List<Appointment> getAppointmentsOfProvider(User provider) {
        return appointmentRepository.findAllByProviderOrderByStartDateTimeUtcAsc(provider);
    }

    /**
     * Returns client appointment notifications.
     *
     * @param client client whose appointment notifications are requested
     * @return appointment views ordered by date and time
     */
    @Transactional(readOnly = true)
    public List<AppointmentView> getAppointmentViewsOfClient(User client) {
        ZoneId clientZone = getClientDisplayTimezone(client);

        return appointmentRepository
                .findAllByClientAndStatusInOrderByStartDateTimeUtcAsc(
                        client,
                        List.of(
                                AppointmentStatus.REQUESTED,
                                AppointmentStatus.PAYMENT_REQUESTED,
                                AppointmentStatus.REJECTED,
                                AppointmentStatus.CANCELLED_BY_PROVIDER,
                                AppointmentStatus.COMPLETED
                        )
                )
                .stream()
                .map(appointment -> viewMapper.toView(appointment, clientZone))
                .toList();
    }

    /**
     * Returns provider appointment requests.
     *
     * @param provider provider whose appointment requests are requested
     * @return requested appointment views ordered by date and time
     */
    @Transactional(readOnly = true)
    public List<AppointmentView> getAppointmentViewsOfProvider(User provider) {
        ZoneId providerZone = providerCalendarService.getProviderTimezone(provider);

        return appointmentRepository
                .findAllByProviderAndStatusOrderByStartDateTimeUtcAsc(
                        provider,
                        AppointmentStatus.REQUESTED
                )
                .stream()
                .map(appointment -> viewMapper.toView(appointment, providerZone))
                .toList();
    }

    /**
     * Returns provider calendar appointments.
     *
     * @param provider provider whose appointments are requested
     * @return provider calendar appointments ordered by date and time
     */
    @Transactional(readOnly = true)
    public List<AppointmentView> getConfirmedAppointmentViewsOfProvider(User provider) {
        ZoneId providerZone = providerCalendarService.getProviderTimezone(provider);

        return appointmentRepository
                .findAllByProviderAndStatusInOrderByStartDateTimeUtcAsc(
                        provider,
                        List.of(
                                AppointmentStatus.CONFIRMED,
                                AppointmentStatus.CANCELLED_BY_CLIENT
                        )
                )
                .stream()
                .map(appointment -> viewMapper.toView(appointment, providerZone))
                .toList();
    }

    /**
     * Returns confirmed appointments of client for calendar section.
     *
     * @param client client whose confirmed appointments are requested
     * @return confirmed appointment views ordered by date and time
     */
    @Transactional(readOnly = true)
    public List<AppointmentView> getConfirmedAppointmentViewsOfClient(User client) {
        ZoneId clientZone = getClientDisplayTimezone(client);

        return appointmentRepository
                .findAllByClientAndStatusOrderByStartDateTimeUtcAsc(
                        client,
                        AppointmentStatus.CONFIRMED
                )
                .stream()
                .map(appointment -> viewMapper.toView(appointment, clientZone))
                .toList();
    }

    /**
     * Returns provider appointment notifications.
     *
     * @param provider provider who owns notifications
     * @return client-cancelled appointment notifications ordered by date and time
     */
    @Transactional(readOnly = true)
    public List<AppointmentView> getAppointmentNotificationViewsOfProvider(User provider) {
        ZoneId providerZone = providerCalendarService.getProviderTimezone(provider);

        return appointmentRepository
                .findAllByProviderAndStatusOrderByStartDateTimeUtcAsc(
                        provider,
                        AppointmentStatus.CANCELLED_BY_CLIENT
                )
                .stream()
                .map(appointment -> viewMapper.toView(appointment, providerZone))
                .toList();
    }

    // ===== Private helpers =====

    /**
     * Returns timezone used to display appointments for client.
     *
     * <p>All appointments of one client belong to the same provider,
     * therefore provider timezone is taken from any existing appointment.
     * Falls back to UTC if client has no appointments yet.
     *
     * @param client authenticated client
     * @return provider timezone, or UTC if no appointments exist
     */
    private ZoneId getClientDisplayTimezone(User client) {
        List<Appointment> appointments = getAppointmentsOfClient(client);

        if (appointments.isEmpty()) {
            return ZoneOffset.UTC;
        }

        User provider = appointments.get(0).getProvider();

        return providerCalendarService.getProviderTimezone(provider);
    }
}