package life.wellnara.service;

import life.wellnara.dto.BookableDateOption;
import life.wellnara.dto.CalendarTerm;
import life.wellnara.model.Appointment;
import life.wellnara.model.AppointmentStatus;
import life.wellnara.model.Offering;
import life.wellnara.model.User;
import life.wellnara.repository.AppointmentRepository;
import life.wellnara.service.time.ApplicationTimeService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Computes provider slot availability for booking.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>compute free calendar terms by subtracting blocking appointments,</li>
 *   <li>enumerate bookable start times per offering duration,</li>
 *   <li>detect scheduling conflicts with existing appointments.</li>
 * </ul>
 *
 * <p>All methods are read-only transactions. No state mutations.
 */
@Service
public class AppointmentAvailabilityService {

	private final AppointmentRepository appointmentRepository;
	private final ProviderCalendarService providerCalendarService;

	private final ApplicationTimeService applicationTimeService;


	private static final int BOOKING_STEP_MINUTES = 15;

	/**
	 * Creates appointment availability service.
	 *
	 * @param appointmentRepository   repository for appointments
	 * @param providerCalendarService service for provider calendar operations
	 */
	public AppointmentAvailabilityService(AppointmentRepository appointmentRepository,
			ProviderCalendarService providerCalendarService,
			ApplicationTimeService applicationTimeService) {
		this.appointmentRepository = appointmentRepository;
		this.providerCalendarService = providerCalendarService;
		this.applicationTimeService = applicationTimeService;
	}

	/**
	 * Returns provider calendar terms excluding already blocked appointments.
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

		return removePastCalendarTerms(result, providerZone);
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
	 * Returns possible booking start times for selected offering on selected date.
	 *
	 * @param provider provider whose calendar is checked
	 * @param offering offering selected by client
	 * @param date     provider-local booking date
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

	/**
	 * Returns possible booking start times inside one free calendar term.
	 *
	 * @param term     free calendar term
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

	/**
	 * Throws if the requested time slot conflicts with any active appointment.
	 *
	 * @param provider         provider whose appointments are checked
	 * @param offering         offering to book
	 * @param startDateTimeUtc requested start time in UTC
	 * @throws IllegalArgumentException if a scheduling conflict is detected
	 */
	public void validateNoConflicts(User provider,
			Offering offering,
			LocalDateTime startDateTimeUtc) {
		LocalDateTime end = startDateTimeUtc.plusMinutes(offering.getDurationMinutes());

		List<Appointment> blocking = getBlockingAppointments(provider);

		for (Appointment existing : blocking) {
			LocalDateTime existingStart = existing.getStartDateTimeUtc();
			LocalDateTime existingEnd = existingStart
					.plusMinutes(existing.getOffering().getDurationMinutes());

			if (overlaps(startDateTimeUtc, end, existingStart, existingEnd)) {
				throw new IllegalArgumentException("Time slot is already booked");
			}
		}
	}

	// ===== Private helpers =====

	private List<CalendarTerm> removePastCalendarTerms(List<CalendarTerm> terms,
			ZoneId providerZone) {
		LocalDate today = applicationTimeService.currentDate(providerZone);
		LocalTime now = applicationTimeService.currentTime(providerZone);

		return terms.stream()
				.filter(term -> term.getDate().isAfter(today)
						|| !term.getStartTime().isBefore(now))
				.toList();
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

	private boolean overlaps(LocalDateTime firstStart,
			LocalDateTime firstEnd,
			LocalDateTime secondStart,
			LocalDateTime secondEnd) {
		return firstStart.isBefore(secondEnd) && firstEnd.isAfter(secondStart);
	}

	private List<CalendarTerm> excludeBlockingAppointments(CalendarTerm term,
			List<Appointment> blockingAppointments,
			ZoneId providerZone) {
		List<CalendarTerm> freeTerms = new ArrayList<>();
		LocalTime freeStart = term.getStartTime();

		List<Appointment> appointmentsOnTermDate = blockingAppointments.stream()
				.filter(appointment ->
				isAppointmentOnDate(appointment, term.getDate(), providerZone))
				.sorted(Comparator.comparing(appointment ->
				toProviderLocalDateTime(appointment, providerZone).toLocalTime()))
				.toList();

		for (Appointment appointment : appointmentsOnTermDate) {
			LocalDateTime appointmentStartLocal =
					toProviderLocalDateTime(appointment, providerZone);
			LocalTime appointmentStart = appointmentStartLocal.toLocalTime();
			LocalTime appointmentEnd = appointmentStart
					.plusMinutes(appointment.getOffering().getDurationMinutes());

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

	private LocalDateTime toProviderLocalDateTime(Appointment appointment, ZoneId providerZone) {
		return appointment.getStartDateTimeUtc()
				.atZone(ZoneOffset.UTC)
				.withZoneSameInstant(providerZone)
				.toLocalDateTime();
	}

	private boolean isAppointmentOnDate(Appointment appointment,
			LocalDate date,
			ZoneId providerZone) {
		return toProviderLocalDateTime(appointment, providerZone)
				.toLocalDate()
				.equals(date);
	}
}