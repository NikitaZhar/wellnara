package life.wellnara;

import life.wellnara.model.Appointment;
import life.wellnara.model.AppointmentStatus;
import life.wellnara.model.AvailabilityDay;
import life.wellnara.model.AvailabilityPeriod;
import life.wellnara.model.AvailabilityRule;
import life.wellnara.model.Offering;
import life.wellnara.model.ProviderClientLink;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for appointment request business logic.
 */
@SpringBootTest
@Transactional
class AppointmentServiceTest {

    private static final String PROVIDER_TIMEZONE = "Europe/Bratislava";

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OfferingRepository offeringRepository;

    @Autowired
    private ProviderClientLinkRepository providerClientLinkRepository;

    @Autowired
    private AvailabilityPeriodRepository availabilityPeriodRepository;

    @Autowired
    private AvailabilityRuleRepository availabilityRuleRepository;

    @Test
    @DisplayName("Should create appointment when all conditions are valid")
    void shouldCreateAppointmentWhenAllConditionsAreValid() {
        User provider = createProvider("provider-valid");
        User client = createClient("client-valid");

        linkClient(provider, client);

        Offering offering = createOffering(provider);

        createAvailability(
                provider,
                AvailabilityDay.MONDAY,
                LocalTime.of(9, 0),
                LocalTime.of(13, 0)
        );

        Appointment appointment = appointmentService.requestAppointment(
                client,
                provider.getId(),
                offering.getId(),
                LocalDateTime.of(2026, 5, 4, 8, 0)
        );

        assertThat(appointment).isNotNull();
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.REQUESTED);
        assertThat(appointment.getProvider().getId()).isEqualTo(provider.getId());
        assertThat(appointment.getClient().getId()).isEqualTo(client.getId());
        assertThat(appointment.getOffering().getId()).isEqualTo(offering.getId());
    }

    @Test
    @DisplayName("Should reject client not linked to provider")
    void shouldRejectClientNotLinkedToProvider() {
        User provider = createProvider("provider-not-linked");
        User client = createClient("client-not-linked");

        Offering offering = createOffering(provider);

        assertThatThrownBy(() ->
                appointmentService.requestAppointment(
                        client,
                        provider.getId(),
                        offering.getId(),
                        LocalDateTime.of(2026, 5, 4, 8, 0)
                )
        ).hasMessageContaining("Client is not linked to provider");
    }

    @Test
    @DisplayName("Should reject offering not belonging to provider")
    void shouldRejectOfferingNotBelongingToProvider() {
        User providerOne = createProvider("provider-one");
        User providerTwo = createProvider("provider-two");
        User client = createClient("client-foreign-offering");

        linkClient(providerOne, client);

        Offering foreignOffering = createOffering(providerTwo);

        assertThatThrownBy(() ->
                appointmentService.requestAppointment(
                        client,
                        providerOne.getId(),
                        foreignOffering.getId(),
                        LocalDateTime.of(2026, 5, 4, 8, 0)
                )
        ).hasMessageContaining("Offering not found for provider");
    }

    @Test
    @DisplayName("Should reject non-client user")
    void shouldRejectNonClientUser() {
        User provider = createProvider("provider-as-client");

        assertThatThrownBy(() ->
                appointmentService.requestAppointment(
                        provider,
                        provider.getId(),
                        1L,
                        LocalDateTime.of(2026, 5, 4, 8, 0)
                )
        ).hasMessageContaining("Only client can request appointment");
    }

    @Test
    @DisplayName("Should reject null start time")
    void shouldRejectNullStartTime() {
        User provider = createProvider("provider-null-start");
        User client = createClient("client-null-start");

        linkClient(provider, client);

        Offering offering = createOffering(provider);

        assertThatThrownBy(() ->
                appointmentService.requestAppointment(
                        client,
                        provider.getId(),
                        offering.getId(),
                        null
                )
        ).hasMessageContaining("Appointment start time is required");
    }

    @Test
    @DisplayName("Should reject appointment outside provider availability")
    void shouldRejectAppointmentOutsideProviderAvailability() {
        User provider = createProvider("provider-outside-availability");
        User client = createClient("client-outside-availability");

        linkClient(provider, client);

        Offering offering = createOffering(provider);

        createAvailability(
                provider,
                AvailabilityDay.MONDAY,
                LocalTime.of(9, 0),
                LocalTime.of(13, 0)
        );

        assertThatThrownBy(() ->
                appointmentService.requestAppointment(
                        client,
                        provider.getId(),
                        offering.getId(),
                        LocalDateTime.of(2026, 5, 4, 12, 30)
                )
        ).hasMessageContaining("Requested time is not available");
    }

    @Test
    @DisplayName("Should reject appointment on day without availability")
    void shouldRejectAppointmentOnDayWithoutAvailability() {
        User provider = createProvider("provider-no-day");
        User client = createClient("client-no-day");

        linkClient(provider, client);

        Offering offering = createOffering(provider);

        createAvailability(
                provider,
                AvailabilityDay.MONDAY,
                LocalTime.of(9, 0),
                LocalTime.of(13, 0)
        );

        assertThatThrownBy(() ->
                appointmentService.requestAppointment(
                        client,
                        provider.getId(),
                        offering.getId(),
                        LocalDateTime.of(2026, 5, 5, 8, 0)
                )
        ).hasMessageContaining("Requested time is not available");
    }

    @Test
    @DisplayName("Should reject appointment when provider has no availability")
    void shouldRejectAppointmentWhenProviderHasNoAvailability() {
        User provider = createProvider("provider-no-availability");
        User client = createClient("client-no-availability");

        linkClient(provider, client);

        Offering offering = createOffering(provider);

        assertThatThrownBy(() ->
                appointmentService.requestAppointment(
                        client,
                        provider.getId(),
                        offering.getId(),
                        LocalDateTime.of(2026, 5, 4, 8, 0)
                )
        ).hasMessageContaining("Requested time is not available");
    }
    
    @Test
    @DisplayName("Should reject appointment when time conflicts with existing appointment")
    void shouldRejectAppointmentWhenTimeConflictsWithExistingAppointment() {
        User provider = createProvider("provider-conflict");
        User firstClient = createClient("client-conflict-one");
        User secondClient = createClient("client-conflict-two");

        linkClient(provider, firstClient);
        linkClient(provider, secondClient);

        Offering offering = createOffering(provider);

        createAvailability(
                provider,
                AvailabilityDay.MONDAY,
                LocalTime.of(9, 0),
                LocalTime.of(13, 0)
        );

        appointmentService.requestAppointment(
                firstClient,
                provider.getId(),
                offering.getId(),
                LocalDateTime.of(2026, 5, 4, 8, 0)
        );

        assertThatThrownBy(() ->
                appointmentService.requestAppointment(
                        secondClient,
                        provider.getId(),
                        offering.getId(),
                        LocalDateTime.of(2026, 5, 4, 8, 30)
                )
        ).hasMessageContaining("Time slot is already booked");
    }
    
    @Test
    @DisplayName("Should allow appointment starting when existing appointment ends")
    void shouldAllowAppointmentStartingWhenExistingAppointmentEnds() {
        User provider = createProvider("provider-no-conflict");
        User firstClient = createClient("client-no-conflict-one");
        User secondClient = createClient("client-no-conflict-two");

        linkClient(provider, firstClient);
        linkClient(provider, secondClient);

        Offering offering = createOffering(provider);

        createAvailability(
                provider,
                AvailabilityDay.MONDAY,
                LocalTime.of(9, 0),
                LocalTime.of(13, 0)
        );

        appointmentService.requestAppointment(
                firstClient,
                provider.getId(),
                offering.getId(),
                LocalDateTime.of(2026, 5, 4, 8, 0)
        );

        Appointment secondAppointment = appointmentService.requestAppointment(
                secondClient,
                provider.getId(),
                offering.getId(),
                LocalDateTime.of(2026, 5, 4, 9, 0)
        );

        assertThat(secondAppointment).isNotNull();
        assertThat(secondAppointment.getStatus()).isEqualTo(AppointmentStatus.REQUESTED);
    }

    private User createProvider(String usernamePrefix) {
        String username = usernamePrefix + "-" + System.nanoTime();

        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@test.com");
        user.setPassword("123");
        user.setRole(UserRole.PROVIDER);

        return userRepository.save(user);
    }

    private User createClient(String usernamePrefix) {
        String username = usernamePrefix + "-" + System.nanoTime();

        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@test.com");
        user.setPassword("123");
        user.setRole(UserRole.CLIENT);

        return userRepository.save(user);
    }

    private void linkClient(User provider, User client) {
        providerClientLinkRepository.save(
                new ProviderClientLink(provider, client, LocalDateTime.now())
        );
    }

    private Offering createOffering(User provider) {
        Offering offering = new Offering(
                provider,
                "Test offering",
                "desc",
                new BigDecimal("100.00"),
                60
        );

        return offeringRepository.save(offering);
    }

    private void createAvailability(User provider,
                                    AvailabilityDay day,
                                    LocalTime startTime,
                                    LocalTime endTime) {
        AvailabilityPeriod period = availabilityPeriodRepository.save(
                new AvailabilityPeriod(
                        provider,
                        LocalDate.of(2026, 5, 1),
                        LocalDate.of(2026, 5, 31),
                        PROVIDER_TIMEZONE
                )
        );

        availabilityRuleRepository.save(
                new AvailabilityRule(
                        period,
                        day,
                        startTime,
                        endTime
                )
        );
    }
    
    @Test
    @DisplayName("Should exclude requested appointment time from bookable times")
    void shouldExcludeRequestedAppointmentTimeFromBookableTimes() {
        User provider = createProvider("provider-bookable");
        User firstClient = createClient("client-bookable-one");
        User secondClient = createClient("client-bookable-two");

        linkClient(provider, firstClient);
        linkClient(provider, secondClient);

        Offering offering = createOffering(provider);

        createAvailability(
                provider,
                AvailabilityDay.MONDAY,
                LocalTime.of(9, 0),
                LocalTime.of(13, 0)
        );

        appointmentService.requestAppointment(
                firstClient,
                provider.getId(),
                offering.getId(),
                LocalDateTime.of(2026, 5, 4, 8, 30)
        );

        List<LocalTime> bookableTimes = appointmentService.getBookableTimes(
                provider,
                offering,
                LocalDate.of(2026, 5, 4)
        );

        assertThat(bookableTimes)
        .doesNotContain(LocalTime.of(10, 0))
        .doesNotContain(LocalTime.of(10, 15))
        .doesNotContain(LocalTime.of(10, 30))
        .doesNotContain(LocalTime.of(10, 45))
        .doesNotContain(LocalTime.of(11, 0))
        .contains(LocalTime.of(9, 0))
        .contains(LocalTime.of(9, 30))
        .contains(LocalTime.of(11, 30))
        .contains(LocalTime.of(12, 0));
    }
}