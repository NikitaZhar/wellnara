package life.wellnara.service;

import life.wellnara.event.AppointmentCancelledEvent;
import life.wellnara.event.AppointmentScheduledEvent;
import life.wellnara.exception.LocalizedException;
import life.wellnara.model.Appointment;
import life.wellnara.model.AppointmentStatus;
import life.wellnara.model.AvailabilityOverrideType;
import life.wellnara.model.CancellationInitiator;
import life.wellnara.model.Offering;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.AppointmentRepository;
import life.wellnara.repository.OfferingRepository;
import life.wellnara.repository.ProviderClientLinkRepository;
import life.wellnara.repository.UserRepository;
import life.wellnara.service.time.ApplicationTimeService;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Executes all state-changing operations in the appointment lifecycle.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>create appointment requests (holding funds, see step 3.5),</li>
 *   <li>drive status transitions (accept, reject, cancel, complete, no-show, …),</li>
 *   <li>release or settle the appointment hold on each terminal transition (step 3.6).</li>
 * </ul>
 *
 * <p>Once an appointment carries a hold it is never deleted; cancelling or expiring
 * it releases the hold and keeps the appointment as history, and dismissing a
 * notification only sets an {@code acknowledged} flag.
 *
 * <p>All precondition violations throw {@link IllegalArgumentException}.
 */
@Service
public class AppointmentCommandService {

	private final AppointmentRepository appointmentRepository;
	private final UserRepository userRepository;
	private final OfferingRepository offeringRepository;
	private final ProviderClientLinkRepository providerClientLinkRepository;
	private final ProviderCalendarService providerCalendarService;
	private final AppointmentAvailabilityService availabilityService;

	private final ApplicationTimeService applicationTimeService;
	private final WalletReservationService walletReservationService;
	private final AppointmentSettlementService settlementService;
	private final ApplicationEventPublisher eventPublisher;


	/**
	 * Creates appointment command service.
	 *
	 * @param appointmentRepository        repository for appointments
	 * @param userRepository               repository for users
	 * @param offeringRepository           repository for offerings
	 * @param providerClientLinkRepository repository for provider-client links
	 * @param providerCalendarService      service for provider calendar operations
	 * @param availabilityService          service for conflict detection
	 * @param applicationTimeService       service for current UTC time
	 * @param walletReservationService     service that holds funds for a requested appointment
	 * @param settlementService            service that releases or settles the appointment hold
	 * @param eventPublisher               publisher of appointment calendar events
	 */
	public AppointmentCommandService(AppointmentRepository appointmentRepository,
			UserRepository userRepository,
			OfferingRepository offeringRepository,
			ProviderClientLinkRepository providerClientLinkRepository,
			ProviderCalendarService providerCalendarService,
			AppointmentAvailabilityService availabilityService,
			ApplicationTimeService applicationTimeService,
			WalletReservationService walletReservationService,
			AppointmentSettlementService settlementService,
			ApplicationEventPublisher eventPublisher) {
		this.appointmentRepository = appointmentRepository;
		this.userRepository = userRepository;
		this.offeringRepository = offeringRepository;
		this.providerClientLinkRepository = providerClientLinkRepository;
		this.providerCalendarService = providerCalendarService;
		this.availabilityService = availabilityService;
		this.applicationTimeService = applicationTimeService;
		this.walletReservationService = walletReservationService;
		this.settlementService = settlementService;
		this.eventPublisher = eventPublisher;
	}

	/**
	 * Creates appointment request from client to provider.
	 *
	 * <p>On success reserves one session for the appointment — a package session if
	 * one covers the offering, otherwise money held against the available balance.
	 * The appointment and its hold are created in one transaction; if the client
	 * cannot cover the session, nothing is created.
	 *
	 * @param client           client who requests appointment
	 * @param providerId       provider identifier
	 * @param offeringId       offering identifier
	 * @param startDateTimeUtc requested start date and time in UTC
	 * @return saved appointment request
	 */
	@Transactional
	public Appointment requestAppointment(User client,
			Long providerId,
			Long offeringId,
			LocalDateTime startDateTimeUtc) {
		validateClient(client);
		validateStartDateTime(startDateTimeUtc);

		User provider = findProvider(providerId);
		validateClientBelongsToProvider(client, provider);

		Offering offering = findProviderOffering(provider, offeringId);
		validateOfferingIsActive(offering);
		validateProviderAvailability(provider, offering, startDateTimeUtc);
		availabilityService.validateNoConflicts(provider, offering, startDateTimeUtc);

		WalletReservationService.Reservation reservation =
				walletReservationService.planReservation(client, offering);
		Appointment appointment = appointmentRepository.save(
				new Appointment(provider, client, offering, startDateTimeUtc));
		walletReservationService.applyReservation(reservation, appointment);

		return appointment;
	}

	/**
	 * Accepts a requested appointment: {@code REQUESTED → SCHEDULED}. The hold is kept.
	 *
	 * @param provider      provider who owns appointment
	 * @param appointmentId appointment identifier
	 */
	@Transactional
	public void acceptAppointment(User provider, Long appointmentId) {
		Appointment appointment = findProviderAppointment(provider, appointmentId);
		requireStatus(appointment, AppointmentStatus.REQUESTED, "error.appt.notRequestedAccept", "Only requested appointment can be accepted");

		appointment.schedule();
		eventPublisher.publishEvent(new AppointmentScheduledEvent(appointment.getId()));
	}

	/**
	 * Rejects a requested appointment, blocks its time slot and releases its hold.
	 *
	 * <p>The freed slot is marked unavailable so the client cannot immediately
	 * re-request the same rejected time (the client is instead told to choose
	 * another time). Blocking is skipped for a request whose start time has already
	 * passed: a past slot cannot be re-requested, and a past-dated override is
	 * rejected by the calendar rules.
	 *
	 * @param provider        provider who owns appointment
	 * @param appointmentId   appointment identifier
	 * @param rejectionReason reason shown to client (required)
	 */
	@Transactional
	public void rejectAppointment(User provider, Long appointmentId, String rejectionReason) {
		requireMessage(rejectionReason, "error.appt.rejectionReasonRequired", "Rejection reason is required");

		Appointment appointment = findProviderAppointment(provider, appointmentId);
		requireStatus(appointment, AppointmentStatus.REQUESTED, "error.appt.notRequestedReject", "Only requested appointment can be rejected");

		if (now().isBefore(appointment.getStartDateTimeUtc())) {
			blockSlot(provider, appointment);
		}
		appointment.cancel(CancellationInitiator.PROVIDER, rejectionReason, now());
		settlementService.release(appointment, appointment.getProvider());
	}

	/**
	 * Cancels a scheduled appointment by the provider and releases its hold.
	 *
	 * <p>The provider may include a message shown to the client. When
	 * {@code blockSlot} is {@code true} the freed time is also marked unavailable
	 * (a one-time UNAVAILABLE override), so the same slot cannot be re-booked and
	 * the client must choose another time; when {@code false} the slot stays open
	 * for re-booking.
	 *
	 * @param provider      provider who owns the appointment
	 * @param appointmentId appointment identifier
	 * @param message       optional message shown to the client
	 * @param blockSlot     whether to block the freed time slot
	 */
	@Transactional
	public void cancelScheduledAppointment(User provider,
			Long appointmentId,
			String message,
			boolean blockSlot) {
		Appointment appointment = findProviderAppointment(provider, appointmentId);
		requireStatus(appointment, AppointmentStatus.SCHEDULED, "error.appt.notScheduledCancel", "Only scheduled appointment can be cancelled");
		requireBeforeStart(appointment, "error.appt.cancelBeforeStart", "Scheduled appointment can only be cancelled before it starts");

		if (blockSlot) {
			blockSlot(provider, appointment);
		}
		appointment.cancel(CancellationInitiator.PROVIDER, message, now());
		settlementService.release(appointment, appointment.getProvider());
		eventPublisher.publishEvent(new AppointmentCancelledEvent(appointment.getId()));
	}

	/**
	 * Cancels a scheduled appointment with no message and the slot left open.
	 *
	 * @param provider      provider who owns the appointment
	 * @param appointmentId appointment identifier
	 */
	@Transactional
	public void cancelScheduledAppointment(User provider, Long appointmentId) {
		cancelScheduledAppointment(provider, appointmentId, null, false);
	}

	/**
	 * Marks a scheduled appointment as completed and settles its hold.
	 *
	 * @param provider      provider who owns appointment
	 * @param appointmentId appointment identifier
	 */
	@Transactional
	public void completeScheduledAppointment(User provider, Long appointmentId) {
		Appointment appointment = findProviderAppointment(provider, appointmentId);
		requireStatus(appointment, AppointmentStatus.SCHEDULED, "error.appt.notScheduledComplete", "Only scheduled appointment can be completed");
		requireStarted(appointment, "error.appt.completeAfterStart", "Appointment can only be completed once it has started");

		appointment.complete();
		settlementService.settle(appointment, appointment.getProvider());
	}

	/**
	 * Marks a scheduled appointment as a no-show and settles its hold.
	 *
	 * @param provider      provider who owns appointment
	 * @param appointmentId appointment identifier
	 */
	@Transactional
	public void markAppointmentNoShow(User provider, Long appointmentId) {
		Appointment appointment = findProviderAppointment(provider, appointmentId);
		requireStatus(appointment, AppointmentStatus.SCHEDULED, "error.appt.notScheduledNoShow", "Only scheduled appointment can be marked as no-show");
		requireStarted(appointment, "error.appt.noShowAfterStart", "Appointment can only be marked as no-show once it has started");

		appointment.markNoShow();
		settlementService.settle(appointment, appointment.getProvider());
	}

	/**
	 * Dismisses a client-cancelled appointment notification for the provider.
	 *
	 * @param provider      provider who acknowledges notification
	 * @param appointmentId appointment identifier
	 */
	@Transactional
	public void acknowledgeProviderAppointmentNotification(User provider, Long appointmentId) {
		Appointment appointment = findProviderAppointment(provider, appointmentId);

		if (!isCancelledBy(appointment, CancellationInitiator.CLIENT)) {
			throw new LocalizedException("error.appt.ackClientCancelledOnly", "Only client-cancelled appointment notification can be acknowledged");
		}

		appointment.acknowledge();
	}

	/**
	 * Cancels a pending request by the client and releases its hold. The appointment
	 * is kept as history.
	 *
	 * @param client        client who owns appointment
	 * @param appointmentId appointment identifier
	 */
	@Transactional
	public void cancelPendingAppointmentByClient(User client, Long appointmentId) {
		Appointment appointment = findClientAppointment(client, appointmentId);
		requireStatus(appointment, AppointmentStatus.REQUESTED, "error.appt.notRequestedCancel", "Only pending appointment can be cancelled");

		appointment.cancel(CancellationInitiator.CLIENT, null, now());
		settlementService.release(appointment, appointment.getClient());
	}

	/**
	 * Cancels a scheduled appointment by client. The hold is released when the
	 * cancellation is at least {@value AppointmentPolicy#CANCEL_REFUND_THRESHOLD_HOURS} hours before the
	 * start, and settled (service treated as delivered) when it is later than that.
	 *
	 * @param client        client who owns appointment
	 * @param appointmentId appointment identifier
	 */
	@Transactional
	public void cancelScheduledAppointmentByClient(User client, Long appointmentId) {
		Appointment appointment = findClientAppointment(client, appointmentId);
		requireStatus(appointment, AppointmentStatus.SCHEDULED, "error.appt.notScheduledCancel", "Only scheduled appointment can be cancelled");

		LocalDateTime now = now();
		appointment.cancel(CancellationInitiator.CLIENT, null, now);

		if (isAtLeastThresholdBeforeStart(appointment, now)) {
			settlementService.release(appointment, appointment.getClient());
		} else {
			settlementService.settle(appointment, appointment.getClient());
		}

		eventPublisher.publishEvent(new AppointmentCancelledEvent(appointment.getId()));
	}

	/**
	 * Reschedules a scheduled appointment on the client's request: the current
	 * booking is cancelled and its hold is <strong>always released</strong> (the
	 * payment is never forfeited on a move), so it can back the new booking the
	 * client places next. Allowed only at least
	 * {@value AppointmentPolicy#RESCHEDULE_MIN_HOURS} hours before the start; closer
	 * to the start only a plain cancellation is offered. The cancelled appointment is
	 * kept as history.
	 *
	 * @param client        client who owns the appointment
	 * @param appointmentId appointment identifier
	 * @return identifier of the offering to re-book, so the caller can send the
	 *         client to that offering's booking screen
	 */
	@Transactional
	public Long rescheduleScheduledAppointmentByClient(User client, Long appointmentId) {
		Appointment appointment = findClientAppointment(client, appointmentId);
		requireStatus(appointment, AppointmentStatus.SCHEDULED, "error.appt.notScheduledReschedule", "Only scheduled appointment can be rescheduled");
		requireReschedulable(appointment);

		appointment.cancel(CancellationInitiator.CLIENT, null, now());
		settlementService.release(appointment, appointment.getClient());
		eventPublisher.publishEvent(new AppointmentCancelledEvent(appointment.getId()));

		return appointment.getOffering().getId();
	}

	/**
	 * Dismisses a provider-cancelled or completed appointment notification for the client.
	 *
	 * @param client        client who acknowledges appointment notification
	 * @param appointmentId appointment identifier
	 */
	@Transactional
	public void acknowledgeRejectedAppointment(User client, Long appointmentId) {
		Appointment appointment = findClientAppointment(client, appointmentId);

		boolean acknowledgeable = isCancelledBy(appointment, CancellationInitiator.PROVIDER)
				|| appointment.getStatus() == AppointmentStatus.COMPLETED;

		if (!acknowledgeable) {
			throw new LocalizedException("error.appt.ackProviderCancelledOrCompleted", "Only provider-cancelled or completed appointment can be acknowledged");
		}

		appointment.acknowledge();
	}

	/**
	 * Expires still-pending requests whose start time has passed: each is cancelled
	 * (as if the provider never accepted) and its hold released. The appointments
	 * are kept as history.
	 */
	@Transactional
	public void expireStaleAppointmentRequests() {
		LocalDateTime now = applicationTimeService.currentUtcDateTime();

		List<Appointment> expired = appointmentRepository.findAllByStatusInAndStartDateTimeUtcBefore(
				List.of(AppointmentStatus.REQUESTED), now);

		for (Appointment appointment : expired) {
			appointment.cancel(CancellationInitiator.PROVIDER, "Request expired", now);
			settlementService.release(appointment, appointment.getProvider());
		}
	}

	// ===== Private helpers =====

	private boolean isAtLeastThresholdBeforeStart(Appointment appointment, LocalDateTime now) {
		return !appointment.getStartDateTimeUtc().isBefore(now.plusHours(AppointmentPolicy.CANCEL_REFUND_THRESHOLD_HOURS));
	}

	private void requireReschedulable(Appointment appointment) {
		if (appointment.getStartDateTimeUtc().isBefore(now().plusHours(AppointmentPolicy.RESCHEDULE_MIN_HOURS))) {
			throw new LocalizedException("error.appt.rescheduleMinHours", "Appointment can only be rescheduled at least " + AppointmentPolicy.RESCHEDULE_MIN_HOURS + " hours before it starts", AppointmentPolicy.RESCHEDULE_MIN_HOURS);
		}
	}

	private void blockSlot(User provider, Appointment appointment) {
		ZoneId providerZone = providerCalendarService.getProviderTimezone(provider);
		LocalDateTime localStart = toProviderLocalDateTime(appointment, providerZone);

		providerCalendarService.createAvailabilityOverride(
				provider,
				localStart.toLocalDate(),
				localStart.toLocalTime(),
				localStart.toLocalTime().plusMinutes(appointment.getOffering().getDurationMinutes()),
				AvailabilityOverrideType.UNAVAILABLE
		);
	}

	private Appointment findProviderAppointment(User provider, Long appointmentId) {
		validateProvider(provider);
		Appointment appointment = findAppointment(appointmentId);

		if (!appointment.getProvider().getId().equals(provider.getId())) {
			throw new LocalizedException("error.appt.notProviderOwned", "Appointment does not belong to provider");
		}

		return appointment;
	}

	private Appointment findClientAppointment(User client, Long appointmentId) {
		validateClient(client);
		Appointment appointment = findAppointment(appointmentId);

		if (!appointment.getClient().getId().equals(client.getId())) {
			throw new LocalizedException("error.appt.notClientOwned", "Appointment does not belong to client");
		}

		return appointment;
	}

	private boolean isCancelledBy(Appointment appointment, CancellationInitiator initiator) {
		return appointment.getStatus() == AppointmentStatus.CANCELLED
				&& appointment.getCancellationInitiator() == initiator;
	}

	private void requireStatus(Appointment appointment, AppointmentStatus expected, String messageKey, String defaultMessage) {
		if (appointment.getStatus() != expected) {
			throw new LocalizedException(messageKey, defaultMessage);
		}
	}

	/**
	 * Requires that the appointment has not started yet (its start time is strictly
	 * in the future relative to the current application time).
	 */
	private void requireBeforeStart(Appointment appointment, String messageKey, String defaultMessage) {
		if (!now().isBefore(appointment.getStartDateTimeUtc())) {
			throw new LocalizedException(messageKey, defaultMessage);
		}
	}

	/**
	 * Requires that the appointment has already started (its start time is now or in
	 * the past relative to the current application time).
	 */
	private void requireStarted(Appointment appointment, String messageKey, String defaultMessage) {
		if (now().isBefore(appointment.getStartDateTimeUtc())) {
			throw new LocalizedException(messageKey, defaultMessage);
		}
	}

	private void requireMessage(String value, String errorKey, String errorDefault) {
		if (value == null || value.isBlank()) {
			throw new LocalizedException(errorKey, errorDefault);
		}
	}

	private LocalDateTime now() {
		return applicationTimeService.currentUtcDateTime();
	}

	private Appointment findAppointment(Long appointmentId) {
		return appointmentRepository.findById(appointmentId)
				.orElseThrow(() -> new LocalizedException("error.appt.notFound", "Appointment not found"));
	}

	private LocalDateTime toProviderLocalDateTime(Appointment appointment, ZoneId providerZone) {
		return appointment.getStartDateTimeUtc()
				.atZone(ZoneOffset.UTC)
				.withZoneSameInstant(providerZone)
				.toLocalDateTime();
	}

	private void validateProviderAvailability(User provider,
			Offering offering,
			LocalDateTime startDateTimeUtc) {
		boolean available = providerCalendarService.isAvailable(
				provider,
				startDateTimeUtc,
				offering.getDurationMinutes()
				);

		if (!available) {
			throw new LocalizedException("error.appt.timeNotAvailable", "Requested time is not available");
		}
	}

	private void validateClient(User client) {
		if (client == null || client.getRole() != UserRole.CLIENT) {
			throw new LocalizedException("error.appt.onlyClientRequest", "Only client can request appointment");
		}
	}

	private void validateProvider(User provider) {
		if (provider == null || provider.getRole() != UserRole.PROVIDER) {
			throw new LocalizedException("error.appt.onlyProviderManage", "Only provider can manage appointment");
		}
	}

	private void validateStartDateTime(LocalDateTime startDateTimeUtc) {
		if (startDateTimeUtc == null) {
			throw new LocalizedException("error.appt.startRequired", "Appointment start time is required");
		}
	}

	private User findProvider(Long providerId) {
		User provider = userRepository.findById(providerId)
				.orElseThrow(() -> new LocalizedException("error.appt.providerNotFound", "Provider not found"));

		if (provider.getRole() != UserRole.PROVIDER) {
			throw new LocalizedException("error.appt.notProvider", "User is not a provider");
		}

		return provider;
	}

	private void validateClientBelongsToProvider(User client, User provider) {
		providerClientLinkRepository.findByProviderAndClientId(provider, client.getId())
		.orElseThrow(() ->
		new LocalizedException("error.appt.clientNotLinked", "Client is not linked to provider"));
	}

	private Offering findProviderOffering(User provider, Long offeringId) {
		return offeringRepository.findByProviderAndId(provider, offeringId)
				.orElseThrow(() ->
				new LocalizedException("error.appt.offeringNotFound", "Offering not found for provider"));
	}

	private void validateOfferingIsActive(Offering offering) {
		if (!offering.isActive()) {
			throw new LocalizedException("error.appt.offeringNotActive", "Offering is not active");
		}
	}
}
