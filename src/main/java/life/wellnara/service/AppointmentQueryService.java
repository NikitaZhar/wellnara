package life.wellnara.service;

import life.wellnara.dto.AppointmentView;
import life.wellnara.model.Appointment;
import life.wellnara.model.AppointmentStatus;
import life.wellnara.model.User;
import life.wellnara.model.UserProfile;
import life.wellnara.repository.AppointmentRepository;
import life.wellnara.service.time.ApplicationTimeService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Provides read-only access to appointment data for UI rendering.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>query appointments by actor and status,</li>
 *   <li>convert results to {@link AppointmentView} via {@link AppointmentViewMapper}.</li>
 * </ul>
 *
 * <p>Client display names are resolved with a single batched profile lookup per call,
 * avoiding one query per appointment.
 *
 * <p>All methods are read-only transactions. No state mutations.
 * For slot availability use {@link AppointmentAvailabilityService}.
 */
@Service
public class AppointmentQueryService {

	private final AppointmentRepository appointmentRepository;
	private final ApplicationTimeService applicationTimeService;
	private final AppointmentViewMapper viewMapper;
	private final UserProfileService userProfileService;

	/**
	 * Creates appointment query service.
	 *
	 * @param appointmentRepository   repository for appointments
	 * @param applicationTimeService  service for application time calculations
	 * @param viewMapper              mapper for Appointment → AppointmentView conversion
	 * @param userProfileService      service for resolving client display names
	 */
	public AppointmentQueryService(AppointmentRepository appointmentRepository,
			ApplicationTimeService applicationTimeService,
			AppointmentViewMapper viewMapper,
			UserProfileService userProfileService) {
		this.appointmentRepository = appointmentRepository;
		this.applicationTimeService = applicationTimeService;
		this.viewMapper = viewMapper;
		this.userProfileService = userProfileService;
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
		List<Appointment> appointments = appointmentRepository
				.findAllByClientAndStatusInOrderByStartDateTimeUtcAsc(
						client,
						List.of(
								AppointmentStatus.REQUESTED,
								AppointmentStatus.PAYMENT_REQUESTED,
								AppointmentStatus.REJECTED,
								AppointmentStatus.CANCELLED_BY_PROVIDER,
								AppointmentStatus.COMPLETED
						)
				);

		return toViews(
				appointments,
				appointment -> applicationTimeService.resolveProviderCalendarZone(appointment.getProvider())
		);
	}

	/**
	 * Returns provider appointment requests.
	 *
	 * @param provider provider whose appointment requests are requested
	 * @return requested appointment views ordered by date and time
	 */
	@Transactional(readOnly = true)
	public List<AppointmentView> getAppointmentViewsOfProvider(User provider) {
		ZoneId providerZone = applicationTimeService.resolveProviderCalendarZone(provider);

		List<Appointment> appointments = appointmentRepository
				.findAllByProviderAndStatusOrderByStartDateTimeUtcAsc(
						provider,
						AppointmentStatus.REQUESTED
				);

		return toViews(appointments, appointment -> providerZone);
	}

	/**
	 * Returns provider calendar appointments.
	 *
	 * @param provider provider whose appointments are requested
	 * @return provider calendar appointments ordered by date and time
	 */
	@Transactional(readOnly = true)
	public List<AppointmentView> getConfirmedAppointmentViewsOfProvider(User provider) {
		ZoneId providerZone = applicationTimeService.resolveProviderCalendarZone(provider);

		List<Appointment> appointments = appointmentRepository
				.findAllByProviderAndStatusInOrderByStartDateTimeUtcAsc(
						provider,
						List.of(
								AppointmentStatus.CONFIRMED,
								AppointmentStatus.CANCELLED_BY_CLIENT
						)
				);

		return toViews(appointments, appointment -> providerZone);
	}

	/**
	 * Returns confirmed appointments of client for calendar section.
	 *
	 * @param client client whose confirmed appointments are requested
	 * @return confirmed appointment views ordered by date and time
	 */
	@Transactional(readOnly = true)
	public List<AppointmentView> getConfirmedAppointmentViewsOfClient(User client) {
		List<Appointment> appointments = appointmentRepository
				.findAllByClientAndStatusOrderByStartDateTimeUtcAsc(
						client,
						AppointmentStatus.CONFIRMED
				);

		return toViews(
				appointments,
				appointment -> applicationTimeService.resolveProviderCalendarZone(appointment.getProvider())
		);
	}

	/**
	 * Returns provider appointment notifications.
	 *
	 * @param provider provider who owns notifications
	 * @return client-cancelled appointment notifications ordered by date and time
	 */
	@Transactional(readOnly = true)
	public List<AppointmentView> getAppointmentNotificationViewsOfProvider(User provider) {
		ZoneId providerZone = applicationTimeService.resolveProviderCalendarZone(provider);

		List<Appointment> appointments = appointmentRepository
				.findAllByProviderAndStatusOrderByStartDateTimeUtcAsc(
						provider,
						AppointmentStatus.CANCELLED_BY_CLIENT
				);

		return toViews(appointments, appointment -> providerZone);
	}

	/**
	 * Converts appointments to views, resolving each client's display name from a single
	 * batched profile lookup.
	 *
	 * @param appointments  appointments to project
	 * @param displayZoneOf resolver of the display timezone for a given appointment
	 * @return appointment views in input order
	 */
	private List<AppointmentView> toViews(List<Appointment> appointments,
			Function<Appointment, ZoneId> displayZoneOf) {
		List<User> clients = appointments.stream()
				.map(Appointment::getClient)
				.distinct()
				.toList();

		Map<Long, UserProfile> profilesByUserId = userProfileService.loadProfilesByUserId(clients);

		return appointments.stream()
				.map(appointment -> {
					User client = appointment.getClient();
					String clientName = userProfileService.displayNameOf(
							client,
							profilesByUserId.get(client.getId())
					);
					return viewMapper.toView(appointment, clientName, displayZoneOf.apply(appointment));
				})
				.toList();
	}
}
