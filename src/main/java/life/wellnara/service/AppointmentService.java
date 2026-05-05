package life.wellnara.service;

import life.wellnara.dto.AppointmentView;
import life.wellnara.dto.BookableDateOption;
import life.wellnara.dto.CalendarTerm;
import life.wellnara.model.Appointment;
import life.wellnara.model.AppointmentStatus;
import life.wellnara.model.Offering;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.AppointmentRepository;
import life.wellnara.repository.OfferingRepository;
import life.wellnara.repository.ProviderClientLinkRepository;
import life.wellnara.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Service for client appointment requests.
 */
@Service
public class AppointmentService {

	private final AppointmentRepository appointmentRepository;
	private final UserRepository userRepository;
	private final OfferingRepository offeringRepository;
	private final ProviderClientLinkRepository providerClientLinkRepository;
	private final ProviderCalendarService providerCalendarService;

	private static final int BOOKING_STEP_MINUTES = 15;

	/**
	 * Creates appointment service.
	 *
	 * @param appointmentRepository repository for appointments
	 * @param userRepository repository for users
	 * @param offeringRepository repository for offerings
	 * @param providerClientLinkRepository repository for provider-client links
	 */
	public AppointmentService(AppointmentRepository appointmentRepository,
			UserRepository userRepository,
			OfferingRepository offeringRepository,
			ProviderClientLinkRepository providerClientLinkRepository,
			ProviderCalendarService providerCalendarService) {
		this.appointmentRepository = appointmentRepository;
		this.userRepository = userRepository;
		this.offeringRepository = offeringRepository;
		this.providerClientLinkRepository = providerClientLinkRepository;
		this.providerCalendarService = providerCalendarService;
	}

	/**
	 * Creates appointment request from client to provider.
	 *
	 * @param client client who requests appointment
	 * @param providerId provider identifier
	 * @param offeringId offering identifier
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
		validateNoConflicts(provider, offering, startDateTimeUtc);

		Appointment appointment = new Appointment(provider, client, offering, startDateTimeUtc);

		return appointmentRepository.save(appointment);
	}

	/**
	 * Validates that requested appointment does not overlap active appointments.
	 *
	 * @param provider provider who owns appointments
	 * @param offering requested offering
	 * @param startDateTimeUtc requested appointment start in UTC
	 */
	private void validateNoConflicts(User provider,
			Offering offering,
			LocalDateTime startDateTimeUtc) {

		LocalDateTime endDateTimeUtc =
				startDateTimeUtc.plusMinutes(offering.getDurationMinutes());

		List<Appointment> activeAppointments = getBlockingAppointments(provider);

		for (Appointment existing : activeAppointments) {
			LocalDateTime existingStart = existing.getStartDateTimeUtc();
			LocalDateTime existingEnd = existingStart
					.plusMinutes(existing.getOffering().getDurationMinutes());

			if (overlaps(startDateTimeUtc, endDateTimeUtc, existingStart, existingEnd)) {
				throw new IllegalArgumentException("Time slot is already booked");
			}
		}
	}

	/**
	 * Checks whether two time intervals overlap.
	 *
	 * @param firstStart first interval start
	 * @param firstEnd first interval end
	 * @param secondStart second interval start
	 * @param secondEnd second interval end
	 * @return true if intervals overlap
	 */
	private boolean overlaps(LocalDateTime firstStart,
			LocalDateTime firstEnd,
			LocalDateTime secondStart,
			LocalDateTime secondEnd) {
		return firstStart.isBefore(secondEnd) && firstEnd.isAfter(secondStart);
	}

	/**
	 * Validates that requested appointment time is inside provider availability.
	 *
	 * @param provider provider who owns availability
	 * @param offering requested offering
	 * @param startDateTimeUtc requested appointment start in UTC
	 */
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

	/**
	 * Validates that user has client role.
	 *
	 * @param client user to validate
	 */
	private void validateClient(User client) {
		if (client == null || client.getRole() != UserRole.CLIENT) {
			throw new IllegalArgumentException("Only client can request appointment");
		}
	}

	/**
	 * Validates appointment start date and time.
	 *
	 * @param startDateTimeUtc requested start date and time
	 */
	private void validateStartDateTime(LocalDateTime startDateTimeUtc) {
		if (startDateTimeUtc == null) {
			throw new IllegalArgumentException("Appointment start time is required");
		}
	}

	/**
	 * Finds provider by id and validates provider role.
	 *
	 * @param providerId provider identifier
	 * @return provider user
	 */
	private User findProvider(Long providerId) {
		User provider = userRepository.findById(providerId)
				.orElseThrow(() -> new IllegalArgumentException("Provider not found"));

		if (provider.getRole() != UserRole.PROVIDER) {
			throw new IllegalArgumentException("User is not a provider");
		}

		return provider;
	}

	/**
	 * Validates that client is linked to provider.
	 *
	 * @param client client user
	 * @param provider provider user
	 */
	private void validateClientBelongsToProvider(User client, User provider) {
		providerClientLinkRepository.findByProviderAndClientId(provider, client.getId())
		.orElseThrow(() -> new IllegalArgumentException("Client is not linked to provider"));
	}

	/**
	 * Finds offering owned by provider.
	 *
	 * @param provider provider owner
	 * @param offeringId offering identifier
	 * @return offering owned by provider
	 */
	private Offering findProviderOffering(User provider, Long offeringId) {
		return offeringRepository.findByProviderAndId(provider, offeringId)
				.orElseThrow(() -> new IllegalArgumentException("Offering not found for provider"));
	}

	/**
	 * Validates that offering can be booked.
	 *
	 * @param offering offering to validate
	 */
	private void validateOfferingIsActive(Offering offering) {
		if (!offering.isActive()) {
			throw new IllegalArgumentException("Offering is not active");
		}
	}

	@Transactional(readOnly = true)
	public List<Appointment> getAppointmentsOfClient(User client) {
		return appointmentRepository.findAllByClientOrderByStartDateTimeUtcAsc(client);
	}

	@Transactional(readOnly = true)
	public List<Appointment> getAppointmentsOfProvider(User provider) {
		return appointmentRepository.findAllByProviderOrderByStartDateTimeUtcAsc(provider);
	}

	private AppointmentView toAppointmentView(Appointment appointment, ZoneId providerZone) {
	    LocalDateTime localDateTime = appointment.getStartDateTimeUtc()
	            .atZone(ZoneOffset.UTC)
	            .withZoneSameInstant(providerZone)
	            .toLocalDateTime();

	    return new AppointmentView(
	            appointment.getId(),
	            appointment.getClient().getUsername(),
	            appointment.getOffering().getName(),
	            localDateTime.toLocalDate(),
	            localDateTime.toLocalTime(),
	            appointment.getStatus(),
	            appointment.getRejectionReason()
	    );
	}

	@Transactional(readOnly = true)
	public List<AppointmentView> getAppointmentViewsOfClient(User client) {
	    ZoneId clientZone = ZoneId.systemDefault();

	    return appointmentRepository
	            .findAllByClientAndStatusInOrderByStartDateTimeUtcAsc(
	                    client,
	                    List.of(
	                            AppointmentStatus.REQUESTED,
	                            AppointmentStatus.PAYMENT_REQUESTED,
	                            AppointmentStatus.REJECTED,
	                            AppointmentStatus.CANCELLED,
	                            AppointmentStatus.COMPLETED
	                    )
	            )
	            .stream()
	            .map(appointment -> toAppointmentView(appointment, clientZone))
	            .toList();
	}

	@Transactional(readOnly = true)
	public List<AppointmentView> getAppointmentViewsOfProvider(User provider) {
	    ZoneId providerZone = providerCalendarService.getProviderTimezone(provider);

	    return appointmentRepository
	            .findAllByProviderAndStatusOrderByStartDateTimeUtcAsc(
	                    provider,
	                    AppointmentStatus.REQUESTED
	            )
	            .stream()
	            .map(appointment -> toAppointmentView(appointment, providerZone))
	            .toList();
	}
	
	/**
	 * Returns confirmed appointments of provider for calendar section.
	 *
	 * @param provider provider whose confirmed appointments are requested
	 * @return confirmed appointment views ordered by date and time
	 */
	@Transactional(readOnly = true)
	public List<AppointmentView> getConfirmedAppointmentViewsOfProvider(User provider) {
	    ZoneId providerZone = providerCalendarService.getProviderTimezone(provider);

	    return appointmentRepository
	            .findAllByProviderAndStatusOrderByStartDateTimeUtcAsc(
	                    provider,
	                    AppointmentStatus.CONFIRMED
	            )
	            .stream()
	            .map(appointment -> toAppointmentView(appointment, providerZone))
	            .toList();
	}
	
	/**
	 * Returns possible booking start times for selected offering on selected date.
	 *
	 * <p>Times are calculated from already free provider calendar terms,
	 * therefore requested and confirmed appointments are excluded.
	 *
	 * @param provider provider whose calendar is checked
	 * @param offering offering selected by client
	 * @param date provider-local booking date
	 * @return available booking start times
	 */
	@Transactional(readOnly = true)
	public List<LocalTime> getBookableTimes(User provider, Offering offering, LocalDate date) {
	    ZoneId providerZone = providerCalendarService.getProviderTimezone(provider);

	    List<CalendarTerm> terms = getFreeCalendarTerms(provider).stream()
	            .filter(term -> term.getDate().equals(date))
	            .toList();

	    List<LocalTime> result = new ArrayList<>();

	    for (CalendarTerm term : terms) {
	        LocalTime latestStart = term.getEndTime()
	                .minusMinutes(offering.getDurationMinutes());

	        if (latestStart.isBefore(term.getStartTime())) {
	            continue;
	        }

	        for (LocalTime current = term.getStartTime();
	             !current.isAfter(latestStart);
	             current = current.plusMinutes(BOOKING_STEP_MINUTES)) {

	            LocalDateTime startUtc = LocalDateTime.of(date, current)
	                    .atZone(providerZone)
	                    .withZoneSameInstant(ZoneOffset.UTC)
	                    .toLocalDateTime();

	            LocalDateTime endUtc = startUtc.plusMinutes(offering.getDurationMinutes());

	            if (!hasConflict(getBlockingAppointments(provider), startUtc, endUtc)) {
	                result.add(current);
	            }
	        }
	    }

	    return result;
	}

	private List<Appointment> getBlockingAppointments(User provider) {
	    return appointmentRepository.findAllByProviderIdAndStatusIn(
	            provider.getId(),
	            List.of(
	                    AppointmentStatus.REQUESTED,
	                    AppointmentStatus.PAYMENT_REQUESTED,
	                    AppointmentStatus.CONFIRMED
	            )
	    );
	}

	private boolean hasConflict(List<Appointment> appointments,
			LocalDateTime start,
			LocalDateTime end) {
		return appointments.stream()
				.anyMatch(existing -> overlaps(
						start,
						end,
						existing.getStartDateTimeUtc(),
						existing.getStartDateTimeUtc()
						.plusMinutes(existing.getOffering().getDurationMinutes())
						));
	}
	
	/**
	 * Returns provider calendar terms excluding already requested or confirmed appointments.
	 *
	 * @param provider provider whose free calendar terms should be returned
	 * @return calendar terms available for booking
	 */
	@Transactional(readOnly = true)
	public List<CalendarTerm> getFreeCalendarTerms(User provider) {
	    ZoneId providerZone = providerCalendarService.getProviderTimezone(provider);
	    List<CalendarTerm> calendarTerms = providerCalendarService.generateCalendar(provider);
	    List<Appointment> blockingAppointments = getBlockingAppointments(provider);

	    List<CalendarTerm> result = new ArrayList<>();

	    for (CalendarTerm term : calendarTerms) {
	        result.addAll(excludeBlockingAppointments(term, blockingAppointments, providerZone));
	    }

	    return result;
	}
	
	/**
	 * Splits one calendar term into free parts by excluding blocking appointments.
	 *
	 * @param term source calendar term
	 * @param blockingAppointments requested or confirmed provider appointments
	 * @param providerZone provider timezone
	 * @return free parts of the original calendar term
	 */
	private List<CalendarTerm> excludeBlockingAppointments(CalendarTerm term,
	        List<Appointment> blockingAppointments,
	        ZoneId providerZone) {

	    List<CalendarTerm> freeTerms = new ArrayList<>();
	    LocalTime freeStart = term.getStartTime();

	    List<Appointment> appointmentsOnTermDate = blockingAppointments.stream()
	            .filter(appointment -> isAppointmentOnDate(appointment, term.getDate(), providerZone))
	            .toList();

	    for (Appointment appointment : appointmentsOnTermDate) {
	        LocalDateTime appointmentStartLocal = toProviderLocalDateTime(appointment, providerZone);
	        LocalTime appointmentStart = appointmentStartLocal.toLocalTime();
	        LocalTime appointmentEnd = appointmentStart.plusMinutes(
	                appointment.getOffering().getDurationMinutes()
	        );

	        if (!overlaps(
	                LocalDateTime.of(term.getDate(), term.getStartTime()),
	                LocalDateTime.of(term.getDate(), term.getEndTime()),
	                LocalDateTime.of(term.getDate(), appointmentStart),
	                LocalDateTime.of(term.getDate(), appointmentEnd)
	        )) {
	            continue;
	        }

	        if (freeStart.isBefore(appointmentStart)) {
	            freeTerms.add(new CalendarTerm(term.getDate(), freeStart, appointmentStart));
	        }

	        if (freeStart.isBefore(appointmentEnd)) {
	            freeStart = appointmentEnd;
	        }
	    }

	    if (freeStart.isBefore(term.getEndTime())) {
	        freeTerms.add(new CalendarTerm(term.getDate(), freeStart, term.getEndTime()));
	    }

	    return freeTerms;
	}
	
	/**
	 * Checks whether appointment starts on given provider-local date.
	 *
	 * @param appointment appointment to check
	 * @param date provider-local date
	 * @param providerZone provider timezone
	 * @return true if appointment starts on given date
	 */
	private boolean isAppointmentOnDate(Appointment appointment, LocalDate date, ZoneId providerZone) {
	    return toProviderLocalDateTime(appointment, providerZone)
	            .toLocalDate()
	            .equals(date);
	}

	/**
	 * Converts appointment start time from UTC to provider-local date and time.
	 *
	 * @param appointment appointment to convert
	 * @param providerZone provider timezone
	 * @return provider-local appointment start date and time
	 */
	private LocalDateTime toProviderLocalDateTime(Appointment appointment, ZoneId providerZone) {
	    return appointment.getStartDateTimeUtc()
	            .atZone(ZoneOffset.UTC)
	            .withZoneSameInstant(providerZone)
	            .toLocalDateTime();
	}
	
	/**
	 * Returns bookable start times grouped by date.
	 *
	 * @param provider provider whose calendar is used
	 * @param offering selected offering
	 * @return bookable date options
	 */
	@Transactional(readOnly = true)
	public List<BookableDateOption> getBookableDateOptions(User provider, Offering offering) {
	    Map<LocalDate, List<LocalTime>> timesByDate = new TreeMap<>();

	    for (CalendarTerm term : getFreeCalendarTerms(provider)) {
	        List<LocalTime> times = getBookableTimesForTerm(term, offering);

	        if (!times.isEmpty()) {
	            timesByDate
	                    .computeIfAbsent(term.getDate(), date -> new ArrayList<>())
	                    .addAll(times);
	        }
	    }

	    List<BookableDateOption> result = new ArrayList<>();

	    for (Map.Entry<LocalDate, List<LocalTime>> entry : timesByDate.entrySet()) {
	        List<LocalTime> times = entry.getValue()
	                .stream()
	                .distinct()
	                .sorted()
	                .collect(Collectors.toList());

	        if (!times.isEmpty()) {
	            result.add(new BookableDateOption(entry.getKey(), times));
	        }
	    }

	    return result;
	}
	
	/**
	 * Returns possible booking start times inside one free calendar term.
	 *
	 * @param term free calendar term
	 * @param offering selected offering
	 * @return possible booking start times
	 */
	public List<LocalTime> getBookableTimesForTerm(CalendarTerm term, Offering offering) {
	    List<LocalTime> result = new ArrayList<>();

	    LocalTime latestStart = term.getEndTime()
	            .minusMinutes(offering.getDurationMinutes());

	    if (latestStart.isBefore(term.getStartTime())) {
	        return result;
	    }

	    for (LocalTime current = term.getStartTime();
	         !current.isAfter(latestStart);
	         current = current.plusMinutes(BOOKING_STEP_MINUTES)) {

	        result.add(current);
	    }

	    return result;
	}
	
	@Transactional
	public void rejectAppointment(User provider, Long appointmentId, String rejectionReason) {
	    validateProvider(provider);

	    Appointment appointment = appointmentRepository.findById(appointmentId)
	            .orElseThrow(() -> new IllegalArgumentException("Appointment not found"));

	    if (!appointment.getProvider().getId().equals(provider.getId())) {
	        throw new IllegalArgumentException("Appointment does not belong to provider");
	    }

	    if (appointment.getStatus() != AppointmentStatus.REQUESTED) {
	        throw new IllegalArgumentException("Only requested appointment can be rejected");
	    }

	    appointment.reject(rejectionReason);
	}
	
	private void validateProvider(User provider) {
	    if (provider == null || provider.getRole() != UserRole.PROVIDER) {
	        throw new IllegalArgumentException("Only provider can manage appointment");
	    }
	}
	
	/**
	 * Deletes rejected appointment after client acknowledgement.
	 *
	 * @param client client who acknowledges rejection
	 * @param appointmentId appointment identifier
	 */
	@Transactional
	public void acknowledgeRejectedAppointment(User client, Long appointmentId) {
	    validateClient(client);

	    Appointment appointment = appointmentRepository.findById(appointmentId)
	            .orElseThrow(() -> new IllegalArgumentException("Appointment not found"));

	    if (!appointment.getClient().getId().equals(client.getId())) {
	        throw new IllegalArgumentException("Appointment does not belong to client");
	    }

	    if (appointment.getStatus() != AppointmentStatus.REJECTED
	            && appointment.getStatus() != AppointmentStatus.CANCELLED
	            && appointment.getStatus() != AppointmentStatus.COMPLETED) {
	        throw new IllegalArgumentException("Only rejected, cancelled or completed appointment can be acknowledged");
	    }

	    appointmentRepository.delete(appointment);
	}
	
	/**
	 * Accepts requested appointment and asks client to complete payment.
	 *
	 * @param provider provider who accepts appointment request
	 * @param appointmentId appointment identifier
	 */
	@Transactional
	public void requestPaymentForAppointment(User provider, Long appointmentId) {
	    validateProvider(provider);

	    Appointment appointment = appointmentRepository.findById(appointmentId)
	            .orElseThrow(() -> new IllegalArgumentException("Appointment not found"));

	    if (!appointment.getProvider().getId().equals(provider.getId())) {
	        throw new IllegalArgumentException("Appointment does not belong to provider");
	    }

	    if (appointment.getStatus() != AppointmentStatus.REQUESTED) {
	        throw new IllegalArgumentException("Only requested appointment can be accepted");
	    }

	    appointment.requestPayment();
	}
	
	/**
	 * Deletes expired unpaid appointment requests.
	 *
	 * <p>Only REQUESTED and PAYMENT_REQUESTED appointments are deleted.
	 * Confirmed, paid, completed or rejected appointments are not affected.
	 */
	@Transactional
	public void deleteExpiredUnpaidAppointments() {
	    List<Appointment> expiredAppointments =
	            appointmentRepository.findAllByStatusInAndStartDateTimeUtcBefore(
	                    List.of(
	                            AppointmentStatus.REQUESTED,
	                            AppointmentStatus.PAYMENT_REQUESTED
	                    ),
	                    LocalDateTime.now(ZoneOffset.UTC)
	            );

	    appointmentRepository.deleteAll(expiredAppointments);
	}
	
	/**
	 * Confirms appointment after fake client payment.
	 *
	 * @param client client who pays for appointment
	 * @param appointmentId appointment identifier
	 */
	@Transactional
	public void payForAppointment(User client, Long appointmentId) {
	    validateClient(client);

	    Appointment appointment = appointmentRepository.findById(appointmentId)
	            .orElseThrow(() -> new IllegalArgumentException("Appointment not found"));

	    if (!appointment.getClient().getId().equals(client.getId())) {
	        throw new IllegalArgumentException("Appointment does not belong to client");
	    }

	    if (appointment.getStatus() != AppointmentStatus.PAYMENT_REQUESTED) {
	        throw new IllegalArgumentException("Only payment requested appointment can be paid");
	    }

	    appointment.confirm();
	}
	
	/**
	 * Cancels confirmed appointment from provider calendar.
	 *
	 * @param provider provider who owns appointment
	 * @param appointmentId appointment identifier
	 */
	@Transactional
	public void cancelConfirmedAppointment(User provider, Long appointmentId) {
	    validateProvider(provider);

	    Appointment appointment = appointmentRepository.findById(appointmentId)
	            .orElseThrow(() -> new IllegalArgumentException("Appointment not found"));

	    if (!appointment.getProvider().getId().equals(provider.getId())) {
	        throw new IllegalArgumentException("Appointment does not belong to provider");
	    }

	    if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
	        throw new IllegalArgumentException("Only confirmed appointment can be cancelled");
	    }

	    appointment.cancel();
	}
	
	/**
	 * Marks confirmed appointment as completed by provider.
	 *
	 * @param provider provider who owns appointment
	 * @param appointmentId appointment identifier
	 */
	@Transactional
	public void completeConfirmedAppointment(User provider, Long appointmentId) {
	    validateProvider(provider);

	    Appointment appointment = appointmentRepository.findById(appointmentId)
	            .orElseThrow(() -> new IllegalArgumentException("Appointment not found"));

	    if (!appointment.getProvider().getId().equals(provider.getId())) {
	        throw new IllegalArgumentException("Appointment does not belong to provider");
	    }

	    if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
	        throw new IllegalArgumentException("Only confirmed appointment can be completed");
	    }

	    appointment.complete();
	}
	
	/**
	 * Returns confirmed appointments of client for calendar section.
	 *
	 * @param client client whose confirmed appointments are requested
	 * @return confirmed appointment views ordered by date and time
	 */
	@Transactional(readOnly = true)
	public List<AppointmentView> getConfirmedAppointmentViewsOfClient(User client) {
	    ZoneId clientZone = ZoneId.systemDefault();

	    return appointmentRepository
	            .findAllByClientAndStatusOrderByStartDateTimeUtcAsc(
	                    client,
	                    AppointmentStatus.CONFIRMED
	            )
	            .stream()
	            .map(appointment -> toAppointmentView(appointment, clientZone))
	            .toList();
	}
}