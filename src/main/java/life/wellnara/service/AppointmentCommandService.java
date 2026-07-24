package life.wellnara.service;

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

	private static final int RELEASE_THRESHOLD_HOURS = 24;

	private final AppointmentRepository appointmentRepository;
	private final UserRepository userRepository;
	private final OfferingRepository offeringRepository;
	private final ProviderClientLinkRepository providerClientLinkRepository;
	private final ProviderCalendarService providerCalendarService;
	private final AppointmentAvailabilityService availabilityService;

	private final ApplicationTimeService applicationTimeService;
	private final WalletReservationService walletReservationService;
	private final AppointmentSettlementService settlementService;


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
	 */
	public AppointmentCommandService(AppointmentRepository appointmentRepository,
			UserRepository userRepository,
			OfferingRepository offeringRepository,
			ProviderClientLinkRepository providerClientLinkRepository,
			ProviderCalendarService providerCalendarService,
			AppointmentAvailabilityService availabilityService,
			ApplicationTimeService applicationTimeService,
			WalletReservationService walletReservationService,
			AppointmentSettlementService settlementService) {
		this.appointmentRepository = appointmentRepository;
		this.userRepository = userRepository;
		this.offeringRepository = offeringRepository;
		this.providerClientLinkRepository = providerClientLinkRepository;
		this.providerCalendarService = providerCalendarService;
		this.availabilityService = availabilityService;
		this.applicationTimeService = applicationTimeService;
		this.walletReservationService = walletReservationService;
		this.settlementService = settlementService;
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
		requireStatus(appointment, AppointmentStatus.REQUESTED, "Only requested appointment can be accepted");

		appointment.schedule();
	}

	/**
	 * Rejects a requested appointment and releases its hold.
	 *
	 * @param provider        provider who owns appointment
	 * @param appointmentId   appointment identifier
	 * @param rejectionReason reason shown to client (required)
	 */
	@Transactional
	public void rejectAppointment(User provider, Long appointmentId, String rejectionReason) {
		requireMessage(rejectionReason, "Rejection reason is required");

		Appointment appointment = findProviderAppointment(provider, appointmentId);
		requireStatus(appointment, AppointmentStatus.REQUESTED, "Only requested appointment can be rejected");

		appointment.cancel(CancellationInitiator.PROVIDER, rejectionReason, now());
		settlementService.release(appointment, appointment.getProvider());
	}

	/**
	 * Cancels a scheduled appointment by provider and releases its hold.
	 *
	 * @param provider      provider who owns appointment
	 * @param appointmentId appointment identifier
	 */
	@Transactional
	public void cancelScheduledAppointment(User provider, Long appointmentId) {
		Appointment appointment = findProviderAppointment(provider, appointmentId);
		requireStatus(appointment, AppointmentStatus.SCHEDULED, "Only scheduled appointment can be cancelled");

		appointment.cancel(CancellationInitiator.PROVIDER, null, now());
		settlementService.release(appointment, appointment.getProvider());
	}

	/**
	 * Reschedules a scheduled appointment by provider: the old slot is freed, the
	 * appointment is cancelled with a message and its hold released, and the client
	 * books a new time (with its own hold).
	 *
	 * @param provider        provider who owns appointment
	 * @param appointmentId   appointment identifier
	 * @param providerMessage message shown to client (required)
	 */
	@Transactional
	public void rescheduleScheduledAppointment(User provider,
			Long appointmentId,
			String providerMessage) {
		requireMessage(providerMessage, "Provider message is required");

		Appointment appointment = findProviderAppointment(provider, appointmentId);
		requireStatus(appointment, AppointmentStatus.SCHEDULED, "Only scheduled appointment can be rescheduled");

		blockSlot(provider, appointment);
		appointment.cancel(CancellationInitiator.PROVIDER, providerMessage, now());
		settlementService.release(appointment, appointment.getProvider());
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
		requireStatus(appointment, AppointmentStatus.SCHEDULED, "Only scheduled appointment can be completed");

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
		requireStatus(appointment, AppointmentStatus.SCHEDULED, "Only scheduled appointment can be marked as no-show");

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
			throw new IllegalArgumentException(
					"Only client-cancelled appointment notification can be acknowledged");
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
		requireStatus(appointment, AppointmentStatus.REQUESTED, "Only pending appointment can be cancelled");

		appointment.cancel(CancellationInitiator.CLIENT, null, now());
		settlementService.release(appointment, appointment.getClient());
	}

	/**
	 * Cancels a scheduled appointment by client. The hold is released when the
	 * cancellation is at least {@value #RELEASE_THRESHOLD_HOURS} hours before the
	 * start, and settled (service treated as delivered) when it is later than that.
	 *
	 * @param client        client who owns appointment
	 * @param appointmentId appointment identifier
	 */
	@Transactional
	public void cancelScheduledAppointmentByClient(User client, Long appointmentId) {
		Appointment appointment = findClientAppointment(client, appointmentId);
		requireStatus(appointment, AppointmentStatus.SCHEDULED, "Only scheduled appointment can be cancelled");

		LocalDateTime now = now();
		appointment.cancel(CancellationInitiator.CLIENT, null, now);

		if (isAtLeastThresholdBeforeStart(appointment, now)) {
			settlementService.release(appointment, appointment.getClient());
		} else {
			settlementService.settle(appointment, appointment.getClient());
		}
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
			throw new IllegalArgumentException(
					"Only provider-cancelled or completed appointment can be acknowledged");
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
		return !appointment.getStartDateTimeUtc().isBefore(now.plusHours(RELEASE_THRESHOLD_HOURS));
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
			throw new IllegalArgumentException("Appointment does not belong to provider");
		}

		return appointment;
	}

	private Appointment findClientAppointment(User client, Long appointmentId) {
		validateClient(client);
		Appointment appointment = findAppointment(appointmentId);

		if (!appointment.getClient().getId().equals(client.getId())) {
			throw new IllegalArgumentException("Appointment does not belong to client");
		}

		return appointment;
	}

	private boolean isCancelledBy(Appointment appointment, CancellationInitiator initiator) {
		return appointment.getStatus() == AppointmentStatus.CANCELLED
				&& appointment.getCancellationInitiator() == initiator;
	}

	private void requireStatus(Appointment appointment, AppointmentStatus expected, String message) {
		if (appointment.getStatus() != expected) {
			throw new IllegalArgumentException(message);
		}
	}

	private void requireMessage(String message, String error) {
		if (message == null || message.isBlank()) {
			throw new IllegalArgumentException(error);
		}
	}

	private LocalDateTime now() {
		return applicationTimeService.currentUtcDateTime();
	}

	private Appointment findAppointment(Long appointmentId) {
		return appointmentRepository.findById(appointmentId)
				.orElseThrow(() -> new IllegalArgumentException("Appointment not found"));
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
			throw new IllegalArgumentException("Requested time is not available");
		}
	}

	private void validateClient(User client) {
		if (client == null || client.getRole() != UserRole.CLIENT) {
			throw new IllegalArgumentException("Only client can request appointment");
		}
	}

	private void validateProvider(User provider) {
		if (provider == null || provider.getRole() != UserRole.PROVIDER) {
			throw new IllegalArgumentException("Only provider can manage appointment");
		}
	}

	private void validateStartDateTime(LocalDateTime startDateTimeUtc) {
		if (startDateTimeUtc == null) {
			throw new IllegalArgumentException("Appointment start time is required");
		}
	}

	private User findProvider(Long providerId) {
		User provider = userRepository.findById(providerId)
				.orElseThrow(() -> new IllegalArgumentException("Provider not found"));

		if (provider.getRole() != UserRole.PROVIDER) {
			throw new IllegalArgumentException("User is not a provider");
		}

		return provider;
	}

	private void validateClientBelongsToProvider(User client, User provider) {
		providerClientLinkRepository.findByProviderAndClientId(provider, client.getId())
		.orElseThrow(() ->
		new IllegalArgumentException("Client is not linked to provider"));
	}

	private Offering findProviderOffering(User provider, Long offeringId) {
		return offeringRepository.findByProviderAndId(provider, offeringId)
				.orElseThrow(() ->
				new IllegalArgumentException("Offering not found for provider"));
	}

	private void validateOfferingIsActive(Offering offering) {
		if (!offering.isActive()) {
			throw new IllegalArgumentException("Offering is not active");
		}
	}
}
