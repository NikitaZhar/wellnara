package life.wellnara.service;

import life.wellnara.model.Appointment;
import life.wellnara.model.AppointmentStatus;
import life.wellnara.model.AvailabilityOverrideType;
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
 *   <li>create appointment requests,</li>
 *   <li>drive status transitions (reject, pay, cancel, complete, …),</li>
 *   <li>delete acknowledged or expired appointments.</li>
 * </ul>
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


	/**
	 * Creates appointment command service.
	 *
	 * @param appointmentRepository        repository for appointments
	 * @param userRepository               repository for users
	 * @param offeringRepository           repository for offerings
	 * @param providerClientLinkRepository repository for provider-client links
	 * @param providerCalendarService      service for provider calendar operations
	 * @param availabilityService          service for conflict detection
	 */
	public AppointmentCommandService(AppointmentRepository appointmentRepository,
			UserRepository userRepository,
			OfferingRepository offeringRepository,
			ProviderClientLinkRepository providerClientLinkRepository,
			ProviderCalendarService providerCalendarService,
			AppointmentAvailabilityService availabilityService,
			ApplicationTimeService applicationTimeService) {
		this.appointmentRepository = appointmentRepository;
		this.userRepository = userRepository;
		this.offeringRepository = offeringRepository;
		this.providerClientLinkRepository = providerClientLinkRepository;
		this.providerCalendarService = providerCalendarService;
		this.availabilityService = availabilityService;
		this.applicationTimeService = applicationTimeService;
	}

	/**
	 * Creates appointment request from client to provider.
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

		return appointmentRepository.save(
				new Appointment(provider, client, offering, startDateTimeUtc));
	}

	/**
	 * Rejects requested appointment by provider.
	 *
	 * @param provider        provider who owns appointment
	 * @param appointmentId   appointment identifier
	 * @param rejectionReason rejection reason
	 */
	@Transactional
	public void rejectAppointment(User provider, Long appointmentId, String rejectionReason) {
		validateProvider(provider);

		Appointment appointment = findAppointment(appointmentId);

		if (!appointment.getProvider().getId().equals(provider.getId())) {
			throw new IllegalArgumentException("Appointment does not belong to provider");
		}

		if (appointment.getStatus() != AppointmentStatus.REQUESTED) {
			throw new IllegalArgumentException("Only requested appointment can be rejected");
		}

		appointment.reject(rejectionReason);
	}

	/**
	 * Accepts requested appointment and asks client to complete payment.
	 *
	 * @param provider      provider who accepts appointment request
	 * @param appointmentId appointment identifier
	 */
	@Transactional
	public void requestPaymentForAppointment(User provider, Long appointmentId) {
		validateProvider(provider);

		Appointment appointment = findAppointment(appointmentId);

		if (!appointment.getProvider().getId().equals(provider.getId())) {
			throw new IllegalArgumentException("Appointment does not belong to provider");
		}

		if (appointment.getStatus() != AppointmentStatus.REQUESTED) {
			throw new IllegalArgumentException("Only requested appointment can be accepted");
		}

		appointment.requestPayment();
	}

	/**
	 * Cancels confirmed appointment by provider.
	 *
	 * @param provider      provider who owns appointment
	 * @param appointmentId appointment identifier
	 */
	@Transactional
	public void cancelConfirmedAppointment(User provider, Long appointmentId) {
		validateProvider(provider);

		Appointment appointment = findAppointment(appointmentId);

		if (!appointment.getProvider().getId().equals(provider.getId())) {
			throw new IllegalArgumentException("Appointment does not belong to provider");
		}

		if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
			throw new IllegalArgumentException("Only confirmed appointment can be cancelled");
		}

		appointment.cancelByProvider();
	}

	/**
	 * Reschedules confirmed appointment by provider.
	 *
	 * <p>The original appointment time is marked as unavailable, and the client
	 * receives a provider message asking to choose a new available time.
	 *
	 * @param provider        provider who owns appointment
	 * @param appointmentId   appointment identifier
	 * @param providerMessage message shown to client
	 */
	@Transactional
	public void rescheduleConfirmedAppointment(User provider,
			Long appointmentId,
			String providerMessage) {
		validateProvider(provider);

		Appointment appointment = findAppointment(appointmentId);

		if (!appointment.getProvider().getId().equals(provider.getId())) {
			throw new IllegalArgumentException("Appointment does not belong to provider");
		}

		if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
			throw new IllegalArgumentException("Only confirmed appointment can be rescheduled");
		}

		ZoneId providerZone = providerCalendarService.getProviderTimezone(provider);
		LocalDateTime localStart = toProviderLocalDateTime(appointment, providerZone);

		providerCalendarService.createAvailabilityOverride(
				provider,
				localStart.toLocalDate(),
				localStart.toLocalTime(),
				localStart.toLocalTime().plusMinutes(appointment.getOffering().getDurationMinutes()),
				AvailabilityOverrideType.UNAVAILABLE
				);

		appointment.cancelByProvider(providerMessage);
	}

	/**
	 * Marks confirmed appointment as completed by provider.
	 *
	 * @param provider      provider who owns appointment
	 * @param appointmentId appointment identifier
	 */
	@Transactional
	public void completeConfirmedAppointment(User provider, Long appointmentId) {
		validateProvider(provider);

		Appointment appointment = findAppointment(appointmentId);

		if (!appointment.getProvider().getId().equals(provider.getId())) {
			throw new IllegalArgumentException("Appointment does not belong to provider");
		}

		if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
			throw new IllegalArgumentException("Only confirmed appointment can be completed");
		}

		appointment.complete();
	}

	/**
	 * Deletes provider appointment notification after acknowledgement.
	 *
	 * @param provider      provider who acknowledges notification
	 * @param appointmentId appointment identifier
	 */
	@Transactional
	public void acknowledgeProviderAppointmentNotification(User provider, Long appointmentId) {
		validateProvider(provider);

		Appointment appointment = findAppointment(appointmentId);

		if (!appointment.getProvider().getId().equals(provider.getId())) {
			throw new IllegalArgumentException("Appointment does not belong to provider");
		}

		if (appointment.getStatus() != AppointmentStatus.CANCELLED_BY_CLIENT) {
			throw new IllegalArgumentException(
					"Only client-cancelled appointment notification can be acknowledged");
		}

		appointmentRepository.delete(appointment);
	}

	/**
	 * Confirms appointment after client payment.
	 *
	 * @param client        client who pays for appointment
	 * @param appointmentId appointment identifier
	 */
	@Transactional
	public void payForAppointment(User client, Long appointmentId) {
		validateClient(client);

		Appointment appointment = findAppointment(appointmentId);

		if (!appointment.getClient().getId().equals(client.getId())) {
			throw new IllegalArgumentException("Appointment does not belong to client");
		}

		if (appointment.getStatus() != AppointmentStatus.PAYMENT_REQUESTED) {
			throw new IllegalArgumentException("Only payment requested appointment can be paid");
		}

		appointment.confirm();
	}

	/**
	 * Deletes pending appointment cancelled by client before confirmation.
	 *
	 * @param client        client who owns appointment
	 * @param appointmentId appointment identifier
	 */
	@Transactional
	public void cancelPendingAppointmentByClient(User client, Long appointmentId) {
		validateClient(client);

		Appointment appointment = findAppointment(appointmentId);

		if (!appointment.getClient().getId().equals(client.getId())) {
			throw new IllegalArgumentException("Appointment does not belong to client");
		}

		if (appointment.getStatus() != AppointmentStatus.REQUESTED
				&& appointment.getStatus() != AppointmentStatus.PAYMENT_REQUESTED) {
			throw new IllegalArgumentException("Only pending appointment can be cancelled");
		}

		appointmentRepository.delete(appointment);
	}

	/**
	 * Cancels confirmed appointment by client.
	 *
	 * @param client        client who owns appointment
	 * @param appointmentId appointment identifier
	 */
	@Transactional
	public void cancelConfirmedAppointmentByClient(User client, Long appointmentId) {
		validateClient(client);

		Appointment appointment = findAppointment(appointmentId);

		if (!appointment.getClient().getId().equals(client.getId())) {
			throw new IllegalArgumentException("Appointment does not belong to client");
		}

		if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
			throw new IllegalArgumentException("Only confirmed appointment can be cancelled");
		}

		appointment.cancelByClient();
	}

	/**
	 * Deletes rejected, provider-cancelled or completed appointment after client acknowledgement.
	 *
	 * @param client        client who acknowledges appointment notification
	 * @param appointmentId appointment identifier
	 */
	@Transactional
	public void acknowledgeRejectedAppointment(User client, Long appointmentId) {
		validateClient(client);

		Appointment appointment = findAppointment(appointmentId);

		if (!appointment.getClient().getId().equals(client.getId())) {
			throw new IllegalArgumentException("Appointment does not belong to client");
		}

		if (appointment.getStatus() != AppointmentStatus.REJECTED
				&& appointment.getStatus() != AppointmentStatus.CANCELLED_BY_PROVIDER
				&& appointment.getStatus() != AppointmentStatus.COMPLETED) {
			throw new IllegalArgumentException(
					"Only rejected, provider-cancelled or completed appointment can be acknowledged");
		}

		appointmentRepository.delete(appointment);
	}

	/**
	 * Deletes expired unpaid appointment requests.
	 *
	 * <p>Only REQUESTED and PAYMENT_REQUESTED appointments are deleted.
	 * Confirmed, paid, completed, rejected or cancelled appointments are not affected.
	 */
	@Transactional
	public void deleteExpiredUnpaidAppointments() {
	    appointmentRepository.deleteAllByStatusInAndStartDateTimeUtcBefore(
	            List.of(
	                    AppointmentStatus.REQUESTED,
	                    AppointmentStatus.PAYMENT_REQUESTED
	            ),
	            applicationTimeService.currentUtcDateTime()
	    );
	}

	// ===== Private helpers =====

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