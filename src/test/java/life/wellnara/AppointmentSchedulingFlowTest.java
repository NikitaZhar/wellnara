package life.wellnara;

import life.wellnara.dto.BookableDateOption;
import life.wellnara.dto.CalendarTerm;
import life.wellnara.model.Appointment;
import life.wellnara.model.AvailabilityDay;
import life.wellnara.model.AvailabilityPeriod;
import life.wellnara.model.AvailabilityRule;
import life.wellnara.model.Offering;
import life.wellnara.model.ProviderClientLink;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.AppointmentRepository;
import life.wellnara.repository.AvailabilityPeriodRepository;
import life.wellnara.repository.AvailabilityRuleRepository;
import life.wellnara.repository.OfferingRepository;
import life.wellnara.repository.ProviderClientLinkRepository;
import life.wellnara.repository.UserRepository;
import life.wellnara.service.AppointmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for appointment scheduling, free interval calculation
 * and bookable time generation.
 */
@SpringBootTest
@Transactional
class AppointmentSchedulingFlowTest {

    private static final String PROVIDER_TIMEZONE = "Europe/Bratislava";

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OfferingRepository offeringRepository;

    @Autowired
    private AvailabilityPeriodRepository availabilityPeriodRepository;

    @Autowired
    private AvailabilityRuleRepository availabilityRuleRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private ProviderClientLinkRepository providerClientLinkRepository;

    /**
     * Verifies that an existing requested appointment splits provider availability
     * into free parts and removes the occupied interval.
     */
    @Test
    @DisplayName("Should split free calendar terms by existing requested appointment")
    void shouldSplitFreeCalendarTermsByExistingRequestedAppointment() {
        User provider = createUser("provider-split", UserRole.PROVIDER);
        User client = createUser("client-split", UserRole.CLIENT);

        Offering offering = createOffering(provider, "Split test", 60);

        createAvailability(
                provider,
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                AvailabilityDay.MONDAY,
                LocalTime.of(9, 0),
                LocalTime.of(15, 0)
        );

        createAppointment(
                provider,
                client,
                offering,
                LocalDate.of(2026, 5, 4),
                LocalTime.of(10, 30)
        );

        List<CalendarTerm> terms = appointmentService.getFreeCalendarTerms(provider);

        assertThat(terms)
                .extracting(term -> term.getDate() + " " + term.getStartTime() + "-" + term.getEndTime())
                .containsExactly(
                        "2026-05-04 09:00-10:30",
                        "2026-05-04 11:30-15:00",
                        "2026-05-11 09:00-15:00",
                        "2026-05-18 09:00-15:00",
                        "2026-05-25 09:00-15:00"
                );
    }

    /**
     * Verifies that occupied intervals on one date are excluded and the remaining
     * bookable times are grouped under the same date.
     */
    @Test
    @DisplayName("Should build bookable times for one date after excluding existing appointments")
    void shouldBuildBookableTimesForOneDateAfterExcludingExistingAppointments() {
        User provider = createUser("provider-bookable", UserRole.PROVIDER);
        User clientOne = createUser("client-bookable-one", UserRole.CLIENT);
        User clientTwo = createUser("client-bookable-two", UserRole.CLIENT);

        Offering shortOffering = createOffering(provider, "Booked 30", 30);
        Offering selectedOffering = createOffering(provider, "Selected 90", 90);

        createAvailability(
                provider,
                LocalDate.of(2026, 4, 29),
                LocalDate.of(2026, 4, 29),
                AvailabilityDay.WEDNESDAY,
                LocalTime.of(10, 0),
                LocalTime.of(15, 0)
        );

        createAppointment(
                provider,
                clientOne,
                shortOffering,
                LocalDate.of(2026, 4, 29),
                LocalTime.of(10, 30)
        );

        createAppointment(
                provider,
                clientTwo,
                shortOffering,
                LocalDate.of(2026, 4, 29),
                LocalTime.of(11, 0)
        );

        List<CalendarTerm> freeTerms = appointmentService.getFreeCalendarTerms(provider);

        assertThat(freeTerms)
                .extracting(term -> term.getDate() + " " + term.getStartTime() + "-" + term.getEndTime())
                .containsExactly(
                        "2026-04-29 10:00-10:30",
                        "2026-04-29 11:30-15:00"
                );

        List<BookableDateOption> options =
                appointmentService.getBookableDateOptions(provider, selectedOffering);

        assertThat(options).hasSize(1);
        assertThat(options.get(0).getDate()).isEqualTo(LocalDate.of(2026, 4, 29));

        assertThat(options.get(0).getTimes()).containsExactly(
                LocalTime.of(11, 30),
                LocalTime.of(11, 45),
                LocalTime.of(12, 0),
                LocalTime.of(12, 15),
                LocalTime.of(12, 30),
                LocalTime.of(12, 45),
                LocalTime.of(13, 0),
                LocalTime.of(13, 15),
                LocalTime.of(13, 30)
        );
    }

    /**
     * Verifies that a short free interval remains visible as free calendar time,
     * but does not become a bookable start time when the selected offering does not fit.
     */
    @Test
    @DisplayName("Should not create bookable time when offering does not fit into short free interval")
    void shouldNotCreateBookableTimeWhenOfferingDoesNotFitIntoShortFreeInterval() {
        User provider = createUser("provider-short-term", UserRole.PROVIDER);
        User client = createUser("client-short-term", UserRole.CLIENT);

        Offering blockingOffering = createOffering(provider, "Blocking 30", 30);
        Offering selectedOffering = createOffering(provider, "Selected 90", 90);

        createAvailability(
                provider,
                LocalDate.of(2026, 4, 29),
                LocalDate.of(2026, 4, 29),
                AvailabilityDay.WEDNESDAY,
                LocalTime.of(10, 0),
                LocalTime.of(15, 0)
        );

        createAppointment(
                provider,
                client,
                blockingOffering,
                LocalDate.of(2026, 4, 29),
                LocalTime.of(10, 30)
        );

        List<CalendarTerm> freeTerms = appointmentService.getFreeCalendarTerms(provider);

        assertThat(freeTerms)
                .extracting(term -> term.getStartTime() + "-" + term.getEndTime())
                .containsExactly(
                        "10:00-10:30",
                        "11:00-15:00"
                );

        List<BookableDateOption> options =
                appointmentService.getBookableDateOptions(provider, selectedOffering);

        assertThat(options).hasSize(1);
        assertThat(options.get(0).getDate()).isEqualTo(LocalDate.of(2026, 4, 29));

        assertThat(options.get(0).getTimes())
                .doesNotContain(LocalTime.of(10, 0))
                .contains(LocalTime.of(11, 0), LocalTime.of(13, 30))
                .doesNotContain(LocalTime.of(13, 45), LocalTime.of(14, 0));
    }

    /**
     * Verifies that a requested appointment blocks the same interval for another client.
     */
    @Test
    @DisplayName("Should reject appointment request when another client already requested overlapping time")
    void shouldRejectAppointmentRequestWhenAnotherClientAlreadyRequestedOverlappingTime() {
        User provider = createUser("provider-conflict", UserRole.PROVIDER);
        User firstClient = createUser("client-conflict-one", UserRole.CLIENT);
        User secondClient = createUser("client-conflict-two", UserRole.CLIENT);

        Offering offering = createOffering(provider, "Conflict offering", 60);

        linkClient(provider, firstClient);
        linkClient(provider, secondClient);

        createAvailability(
                provider,
                LocalDate.of(2026, 5, 4),
                LocalDate.of(2026, 5, 4),
                AvailabilityDay.MONDAY,
                LocalTime.of(9, 0),
                LocalTime.of(15, 0)
        );

        createAppointment(
                provider,
                firstClient,
                offering,
                LocalDate.of(2026, 5, 4),
                LocalTime.of(10, 0)
        );

        assertThatThrownBy(() -> appointmentService.requestAppointment(
                secondClient,
                provider.getId(),
                offering.getId(),
                toUtc(LocalDate.of(2026, 5, 4), LocalTime.of(10, 15))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Time slot is already booked");
    }

    /**
     * Verifies that cancelled appointments do not block provider free time.
     */
    @Test
    @DisplayName("Should ignore cancelled appointments when calculating free calendar terms")
    void shouldIgnoreCancelledAppointmentsWhenCalculatingFreeCalendarTerms() {
        User provider = createUser("provider-cancelled", UserRole.PROVIDER);
        User client = createUser("client-cancelled", UserRole.CLIENT);

        Offering offering = createOffering(provider, "Cancelled offering", 60);

        createAvailability(
                provider,
                LocalDate.of(2026, 5, 4),
                LocalDate.of(2026, 5, 4),
                AvailabilityDay.MONDAY,
                LocalTime.of(9, 0),
                LocalTime.of(15, 0)
        );

        Appointment appointment = createAppointment(
                provider,
                client,
                offering,
                LocalDate.of(2026, 5, 4),
                LocalTime.of(10, 0)
        );

        appointment.cancelByClient();
        appointmentRepository.save(appointment);

        List<CalendarTerm> terms = appointmentService.getFreeCalendarTerms(provider);

        assertThat(terms)
                .extracting(term -> term.getDate() + " " + term.getStartTime() + "-" + term.getEndTime())
                .containsExactly("2026-05-04 09:00-15:00");
    }

    /**
     * Creates and saves user.
     *
     * @param username username prefix
     * @param role user role
     * @return saved user
     */
    private User createUser(String username, UserRole role) {
        User user = new User();
        user.setUsername(username + "-" + System.nanoTime());
        user.setEmail(user.getUsername() + "@test.com");
        user.setPassword("123");
        user.setRole(role);

        return userRepository.save(user);
    }

    /**
     * Creates and saves offering.
     *
     * @param provider provider user
     * @param name offering name
     * @param durationMinutes offering duration in minutes
     * @return saved offering
     */
    private Offering createOffering(User provider, String name, int durationMinutes) {
        Offering offering = new Offering(
                provider,
                name,
                "Test description",
                new BigDecimal("100.00"),
                durationMinutes
        );

        return offeringRepository.save(offering);
    }

    /**
     * Creates and saves provider availability.
     *
     * @param provider provider user
     * @param from period start date
     * @param to period end date
     * @param day availability weekday
     * @param start availability start time
     * @param end availability end time
     */
    private void createAvailability(User provider,
                                    LocalDate from,
                                    LocalDate to,
                                    AvailabilityDay day,
                                    LocalTime start,
                                    LocalTime end) {
        AvailabilityPeriod period = availabilityPeriodRepository.save(
                new AvailabilityPeriod(provider, from, to, PROVIDER_TIMEZONE)
        );

        availabilityRuleRepository.save(
                new AvailabilityRule(period, day, start, end)
        );
    }

    /**
     * Creates and saves requested appointment at provider-local date and time.
     *
     * @param provider provider user
     * @param client client user
     * @param offering appointment offering
     * @param date provider-local date
     * @param time provider-local start time
     * @return saved appointment
     */
    private Appointment createAppointment(User provider,
                                          User client,
                                          Offering offering,
                                          LocalDate date,
                                          LocalTime time) {
        Appointment appointment = new Appointment(
                provider,
                client,
                offering,
                toUtc(date, time)
        );

        return appointmentRepository.save(appointment);
    }

    /**
     * Links client to provider.
     *
     * @param provider provider user
     * @param client client user
     */
    private void linkClient(User provider, User client) {
        providerClientLinkRepository.save(
                new ProviderClientLink(provider, client, LocalDateTime.now())
        );
    }

    /**
     * Converts provider-local date and time to UTC local date-time.
     *
     * @param date provider-local date
     * @param time provider-local time
     * @return UTC date-time without timezone
     */
    private LocalDateTime toUtc(LocalDate date, LocalTime time) {
        return LocalDateTime.of(date, time)
                .atZone(ZoneId.of(PROVIDER_TIMEZONE))
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }
}