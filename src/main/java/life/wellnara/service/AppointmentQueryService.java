package life.wellnara.service;

import life.wellnara.dto.AppointmentView;
import life.wellnara.model.Appointment;
import life.wellnara.model.AppointmentStatus;
import life.wellnara.model.CancellationInitiator;
import life.wellnara.model.User;
import life.wellnara.model.UserProfile;
import life.wellnara.repository.AppointmentRepository;
import life.wellnara.service.time.ApplicationTimeService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
 * <p>Notification lists exclude acknowledged appointments — those stay in history
 * but have been dismissed from the cards.
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
	 * Returns client appointment notifications: own pending requests,
	 * provider-cancelled appointments, and completed ones. The client's own
	 * cancellations and dismissed notifications are not shown.
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
								AppointmentStatus.CANCELLED,
								AppointmentStatus.COMPLETED
						)
				)
				.stream()
				.filter(appointment -> !appointment.isAcknowledged())
				.filter(appointment -> !cancelledBy(appointment, CancellationInitiator.CLIENT))
				.toList();

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
	 * Returns provider calendar appointments: scheduled appointments and
	 * undismissed client-cancelled notifications.
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
								AppointmentStatus.SCHEDULED,
								AppointmentStatus.CANCELLED
						)
				)
				.stream()
				.filter(appointment -> appointment.getStatus() == AppointmentStatus.SCHEDULED
						|| (!appointment.isAcknowledged()
								&& cancelledBy(appointment, CancellationInitiator.CLIENT)))
				.toList();

		return toViews(appointments, appointment -> providerZone);
	}

	/**
	 * Returns scheduled appointments of client for calendar section.
	 *
	 * @param client client whose scheduled appointments are requested
	 * @return scheduled appointment views ordered by date and time
	 */
	@Transactional(readOnly = true)
	public List<AppointmentView> getConfirmedAppointmentViewsOfClient(User client) {
		List<Appointment> appointments = appointmentRepository
				.findAllByClientAndStatusOrderByStartDateTimeUtcAsc(
						client,
						AppointmentStatus.SCHEDULED
				);

		List<AppointmentView> views = toViews(
				appointments,
				appointment -> applicationTimeService.resolveProviderCalendarZone(appointment.getProvider())
		);

		applyClientActionWindows(appointments, views);

		return views;
	}

	/**
	 * Marks, per confirmed appointment, whether the client may still reschedule it and
	 * whether cancelling it now forfeits the payment. Reschedule is offered only at
	 * least {@value AppointmentPolicy#RESCHEDULE_MIN_HOURS} hours before the start (and
	 * always keeps the payment), while a cancellation within
	 * {@value AppointmentPolicy#CANCEL_REFUND_THRESHOLD_HOURS} hours forfeits it. The
	 * two lists are aligned by index.
	 *
	 * @param appointments source appointments
	 * @param views        views to enrich, in the same order
	 */
	private void applyClientActionWindows(List<Appointment> appointments, List<AppointmentView> views) {
		LocalDateTime nowUtc = applicationTimeService.currentUtcDateTime();

		for (int i = 0; i < appointments.size(); i++) {
			LocalDateTime startUtc = appointments.get(i).getStartDateTimeUtc();
			AppointmentView view = views.get(i);

			view.setReschedulable(
					!startUtc.isBefore(nowUtc.plusHours(AppointmentPolicy.RESCHEDULE_MIN_HOURS)));
			view.setCancelForfeitsPayment(
					startUtc.isBefore(nowUtc.plusHours(AppointmentPolicy.CANCEL_REFUND_THRESHOLD_HOURS)));
		}
	}

	/**
	 * Returns provider appointment notifications.
	 *
	 * @param provider provider who owns notifications
	 * @return undismissed client-cancelled appointment notifications ordered by date and time
	 */
	@Transactional(readOnly = true)
	public List<AppointmentView> getAppointmentNotificationViewsOfProvider(User provider) {
		ZoneId providerZone = applicationTimeService.resolveProviderCalendarZone(provider);

		List<Appointment> appointments = appointmentRepository
				.findAllByProviderAndStatusOrderByStartDateTimeUtcAsc(
						provider,
						AppointmentStatus.CANCELLED
				)
				.stream()
				.filter(appointment -> !appointment.isAcknowledged())
				.filter(appointment -> cancelledBy(appointment, CancellationInitiator.CLIENT))
				.toList();

		return toViews(appointments, appointment -> providerZone);
	}

	private boolean cancelledBy(Appointment appointment, CancellationInitiator initiator) {
		return appointment.getStatus() == AppointmentStatus.CANCELLED
				&& appointment.getCancellationInitiator() == initiator;
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
