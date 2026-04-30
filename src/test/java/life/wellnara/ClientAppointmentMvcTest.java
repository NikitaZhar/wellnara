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
import life.wellnara.repository.AppointmentRepository;
import life.wellnara.repository.AvailabilityPeriodRepository;
import life.wellnara.repository.AvailabilityRuleRepository;
import life.wellnara.repository.OfferingRepository;
import life.wellnara.repository.ProviderClientLinkRepository;
import life.wellnara.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MVC tests for client appointment request flow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClientAppointmentMvcTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

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

    @Autowired
    private AppointmentRepository appointmentRepository;

    /**
     * Verifies that client can create appointment request from selected date and time.
     *
     * @throws Exception if MVC request fails
     */
    @Test
    @DisplayName("Should create appointment request from selected date and time")
    void shouldCreateAppointmentRequestFromSelectedDateAndTime() throws Exception {
        User provider = createProvider("provider-client-mvc");
        User client = createClient("client-mvc");

        linkClient(provider, client);

        Offering offering = createOffering(provider, 60);

        createAvailability(provider);

        MockHttpSession session = createSessionWithCurrentUser(client);

        mockMvc.perform(post("/client/appointments")
                        .session(session)
                        .param("providerId", provider.getId().toString())
                        .param("offeringId", offering.getId().toString())
                        .param("selectedDate", "2026-05-04")
                        .param("selectedTime", "10:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/client"));

        assertThat(appointmentRepository.findAllByProviderOrderByStartDateTimeUtcAsc(provider))
                .hasSize(1);

        Appointment appointment = appointmentRepository
                .findAllByProviderOrderByStartDateTimeUtcAsc(provider)
                .get(0);

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.REQUESTED);
        assertThat(appointment.getProvider().getId()).isEqualTo(provider.getId());
        assertThat(appointment.getClient().getId()).isEqualTo(client.getId());
        assertThat(appointment.getOffering().getId()).isEqualTo(offering.getId());

        /*
         * Provider timezone is Europe/Bratislava.
         * 2026-05-04 10:00 local time is 2026-05-04 08:00 UTC.
         */
        assertThat(appointment.getStartDateTimeUtc())
                .isEqualTo(LocalDateTime.of(2026, 5, 4, 8, 0));
    }

    /**
     * Verifies that client receives error when selected time is outside provider availability.
     *
     * @throws Exception if MVC request fails
     */
    @Test
    @DisplayName("Should show appointment error when client requests unavailable time")
    void shouldShowAppointmentErrorWhenClientRequestsUnavailableTime() throws Exception {
        User provider = createProvider("provider-client-mvc-error");
        User client = createClient("client-mvc-error");

        linkClient(provider, client);

        Offering offering = createOffering(provider, 60);

        createAvailability(provider);

        MockHttpSession session = createSessionWithCurrentUser(client);

        mockMvc.perform(post("/client/appointments")
                        .session(session)
                        .param("providerId", provider.getId().toString())
                        .param("offeringId", offering.getId().toString())
                        .param("selectedDate", "2026-05-04")
                        .param("selectedTime", "15:00"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("appointmentError"))
                .andExpect(model().attribute("appointmentError", "Requested time is not available"));

        assertThat(appointmentRepository.findAllByProviderOrderByStartDateTimeUtcAsc(provider))
                .isEmpty();
    }

    /**
     * Verifies that requested appointment blocks another overlapping client request.
     *
     * @throws Exception if MVC request fails
     */
    @Test
    @DisplayName("Should show appointment error when selected time overlaps existing request")
    void shouldShowAppointmentErrorWhenSelectedTimeOverlapsExistingRequest() throws Exception {
        User provider = createProvider("provider-client-mvc-conflict");
        User firstClient = createClient("client-mvc-conflict-one");
        User secondClient = createClient("client-mvc-conflict-two");

        linkClient(provider, firstClient);
        linkClient(provider, secondClient);

        Offering offering = createOffering(provider, 60);

        createAvailability(provider);

        appointmentRepository.save(new Appointment(
                provider,
                firstClient,
                offering,
                LocalDateTime.of(2026, 5, 4, 8, 0)
        ));

        MockHttpSession secondClientSession = createSessionWithCurrentUser(secondClient);

        mockMvc.perform(post("/client/appointments")
                        .session(secondClientSession)
                        .param("providerId", provider.getId().toString())
                        .param("offeringId", offering.getId().toString())
                        .param("selectedDate", "2026-05-04")
                        .param("selectedTime", "10:15"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("appointmentError"))
                .andExpect(model().attribute("appointmentError", "Time slot is already booked"));

        assertThat(appointmentRepository.findAllByProviderOrderByStartDateTimeUtcAsc(provider))
                .hasSize(1);
    }

    /**
     * Verifies that unauthenticated user is redirected to login page.
     *
     * @throws Exception if MVC request fails
     */
    @Test
    @DisplayName("Should redirect unauthenticated user to login when requesting appointment")
    void shouldRedirectUnauthenticatedUserToLoginWhenRequestingAppointment() throws Exception {
        mockMvc.perform(post("/client/appointments")
                        .param("providerId", "1")
                        .param("offeringId", "1")
                        .param("selectedDate", "2026-05-04")
                        .param("selectedTime", "10:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login"));
    }

    /**
     * Creates provider user.
     *
     * @param usernamePrefix username prefix
     * @return saved provider
     */
    private User createProvider(String usernamePrefix) {
        String username = usernamePrefix + "-" + System.nanoTime();

        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@test.com");
        user.setPassword("123");
        user.setRole(UserRole.PROVIDER);

        return userRepository.save(user);
    }

    /**
     * Creates client user.
     *
     * @param usernamePrefix username prefix
     * @return saved client
     */
    private User createClient(String usernamePrefix) {
        String username = usernamePrefix + "-" + System.nanoTime();

        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@test.com");
        user.setPassword("123");
        user.setRole(UserRole.CLIENT);

        return userRepository.save(user);
    }

    /**
     * Creates offering for provider.
     *
     * @param provider provider user
     * @param durationMinutes offering duration
     * @return saved offering
     */
    private Offering createOffering(User provider, int durationMinutes) {
        Offering offering = new Offering(
                provider,
                "Consultation",
                "Test consultation",
                new BigDecimal("100.00"),
                durationMinutes
        );

        return offeringRepository.save(offering);
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
     * Creates provider availability for Monday 09:00-13:00 Europe/Bratislava.
     *
     * @param provider provider user
     */
    private void createAvailability(User provider) {
        AvailabilityPeriod period = availabilityPeriodRepository.save(
                new AvailabilityPeriod(
                        provider,
                        LocalDate.of(2026, 5, 1),
                        LocalDate.of(2026, 5, 31),
                        "Europe/Bratislava"
                )
        );

        availabilityRuleRepository.save(
                new AvailabilityRule(
                        period,
                        AvailabilityDay.MONDAY,
                        LocalTime.of(9, 0),
                        LocalTime.of(13, 0)
                )
        );
    }

    /**
     * Creates authenticated session for current user.
     *
     * @param user authenticated user
     * @return mock HTTP session
     */
    private MockHttpSession createSessionWithCurrentUser(User user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", user);
        return session;
    }
}