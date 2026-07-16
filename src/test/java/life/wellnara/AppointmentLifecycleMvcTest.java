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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static life.wellnara.SecurityTestSupport.authenticatedSession;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MVC tests for provider appointment actions and client acknowledgement flow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(AppointmentLifecycleMvcTest.FixedClockConfig.class)
class AppointmentLifecycleMvcTest {

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

    @Test
    @DisplayName("Should reject requested appointment with reason")
    void shouldRejectRequestedAppointmentWithReason() throws Exception {
        User provider = createUser("provider-reject", UserRole.PROVIDER);
        User client = createUser("client-reject", UserRole.CLIENT);
        linkClient(provider, client);

        Offering offering = createOffering(provider);
        Appointment appointment = createAppointment(provider, client, offering);

        MockHttpSession providerSession = authenticatedSession(provider);

        mockMvc.perform(post("/provider/appointments/{appointmentId}/reject", appointment.getId()).with(csrf())
                        .session(providerSession)
                        .param("rejectionReason", "Requested time is not suitable"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider?section=provider-calendar"));

        Appointment savedAppointment = appointmentRepository.findById(appointment.getId())
                .orElseThrow();

        assertThat(savedAppointment.getStatus()).isEqualTo(AppointmentStatus.REJECTED);
        assertThat(savedAppointment.getRejectionReason())
                .isEqualTo("Requested time is not suitable");
    }

    @Test
    @DisplayName("Should delete rejected appointment after client acknowledgement")
    void shouldDeleteRejectedAppointmentAfterClientAcknowledgement() throws Exception {
        User provider = createUser("provider-ack", UserRole.PROVIDER);
        User client = createUser("client-ack", UserRole.CLIENT);
        linkClient(provider, client);

        Offering offering = createOffering(provider);
        Appointment appointment = createAppointment(provider, client, offering);
        appointment.reject("Not available");
        appointmentRepository.save(appointment);

        MockHttpSession clientSession = authenticatedSession(client);

        mockMvc.perform(post("/client/appointments/{appointmentId}/acknowledge", appointment.getId()).with(csrf())
                        .session(clientSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/client?section=calendar"));

        assertThat(appointmentRepository.findById(appointment.getId())).isEmpty();
    }

    @Test
    @DisplayName("Should request payment and keep appointment slot blocked")
    void shouldRequestPaymentAndKeepAppointmentSlotBlocked() throws Exception {
        User provider = createUser("provider-payment", UserRole.PROVIDER);
        User firstClient = createUser("client-payment-one", UserRole.CLIENT);
        User secondClient = createUser("client-payment-two", UserRole.CLIENT);

        linkClient(provider, firstClient);
        linkClient(provider, secondClient);

        Offering offering = createOffering(provider);
        createAvailability(provider);

        Appointment appointment = createAppointment(provider, firstClient, offering);

        MockHttpSession providerSession = authenticatedSession(provider);

        mockMvc.perform(post("/provider/appointments/{appointmentId}/request-payment", appointment.getId()).with(csrf())
                        .session(providerSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider?section=provider-calendar"));

        Appointment savedAppointment = appointmentRepository.findById(appointment.getId())
                .orElseThrow();

        assertThat(savedAppointment.getStatus()).isEqualTo(AppointmentStatus.PAYMENT_REQUESTED);

        MockHttpSession secondClientSession = authenticatedSession(secondClient);

        mockMvc.perform(post("/client/appointments").with(csrf())
                        .session(secondClientSession)
                        .param("providerId", provider.getId().toString())
                        .param("offeringId", offering.getId().toString())
                        .param("selectedDate", "2026-06-01")
                        .param("selectedTime", "10:00"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("appointmentError"))
                .andExpect(model().attribute("appointmentError", "Time slot is already booked"));
    }

    private User createUser(String usernamePrefix, UserRole role) {
        String username = usernamePrefix + "-" + System.nanoTime();

        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@test.com");
        user.setPassword("123");
        user.setRole(role);

        return userRepository.save(user);
    }

    private Offering createOffering(User provider) {
        Offering offering = new Offering(
                provider,
                "Consultation",
                "Test consultation",
                new BigDecimal("100.00"),
                60
        );

        return offeringRepository.save(offering);
    }

    private Appointment createAppointment(User provider, User client, Offering offering) {
        Appointment appointment = new Appointment(
                provider,
                client,
                offering,
                LocalDateTime.of(2026, 6, 1, 8, 0)
        );

        return appointmentRepository.save(appointment);
    }

    private void createAvailability(User provider) {
        AvailabilityPeriod period = availabilityPeriodRepository.save(
                new AvailabilityPeriod(
                        provider,
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 30),
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

    private void linkClient(User provider, User client) {
        providerClientLinkRepository.save(
                new ProviderClientLink(provider, client, LocalDateTime.now())
        );
    }

    
    @Test
    @DisplayName("Should confirm appointment after fake client payment")
    void shouldConfirmAppointmentAfterFakeClientPayment() throws Exception {
        User provider = createUser("provider-fake-pay", UserRole.PROVIDER);
        User client = createUser("client-fake-pay", UserRole.CLIENT);
        linkClient(provider, client);

        Offering offering = createOffering(provider);
        Appointment appointment = createAppointment(provider, client, offering);
        appointment.requestPayment();
        appointmentRepository.save(appointment);

        MockHttpSession clientSession = authenticatedSession(client);

        mockMvc.perform(post("/client/appointments/{appointmentId}/pay", appointment.getId()).with(csrf())
                        .session(clientSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/client?section=calendar"));

        Appointment savedAppointment = appointmentRepository.findById(appointment.getId())
                .orElseThrow();

        assertThat(savedAppointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
    }
    
    @Test
    @DisplayName("Should cancel confirmed appointment by client through MVC")
    void shouldCancelConfirmedAppointmentByClientThroughMvc() throws Exception {
        User provider = createUser("provider-client-cancel", UserRole.PROVIDER);
        User client = createUser("client-client-cancel", UserRole.CLIENT);
        linkClient(provider, client);

        Offering offering = createOffering(provider);
        Appointment appointment = createAppointment(provider, client, offering);
        appointment.confirm();
        appointmentRepository.save(appointment);

        MockHttpSession clientSession = authenticatedSession(client);

        mockMvc.perform(post("/client/appointments/{appointmentId}/cancel", appointment.getId()).with(csrf())
                        .session(clientSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/client?section=calendar"));

        Appointment savedAppointment = appointmentRepository.findById(appointment.getId())
                .orElseThrow();

        assertThat(savedAppointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED_BY_CLIENT);
    }

    @Test
    @DisplayName("Should cancel confirmed appointment by provider through MVC")
    void shouldCancelConfirmedAppointmentByProviderThroughMvc() throws Exception {
        User provider = createUser("provider-provider-cancel", UserRole.PROVIDER);
        User client = createUser("client-provider-cancel", UserRole.CLIENT);
        linkClient(provider, client);

        Offering offering = createOffering(provider);
        Appointment appointment = createAppointment(provider, client, offering);
        appointment.confirm();
        appointmentRepository.save(appointment);

        MockHttpSession providerSession = authenticatedSession(provider);

        mockMvc.perform(post("/provider/appointments/{appointmentId}/cancel", appointment.getId()).with(csrf())
                        .session(providerSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider?section=provider-calendar"));

        Appointment savedAppointment = appointmentRepository.findById(appointment.getId())
                .orElseThrow();

        assertThat(savedAppointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED_BY_PROVIDER);
    }

    @Test
    @DisplayName("Should reschedule confirmed appointment by provider through MVC")
    void shouldRescheduleConfirmedAppointmentByProviderThroughMvc() throws Exception {
        User provider = createUser("provider-reschedule", UserRole.PROVIDER);
        User client = createUser("client-reschedule", UserRole.CLIENT);
        linkClient(provider, client);

        Offering offering = createOffering(provider);
        Appointment appointment = createAppointment(provider, client, offering);
        appointment.confirm();
        appointmentRepository.save(appointment);

        MockHttpSession providerSession = authenticatedSession(provider);

        mockMvc.perform(post("/provider/appointments/{appointmentId}/reschedule", appointment.getId()).with(csrf())
                        .session(providerSession)
                        .param("providerMessage", "Please choose another available time"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider?section=provider-calendar"));

        Appointment savedAppointment = appointmentRepository.findById(appointment.getId())
                .orElseThrow();

        assertThat(savedAppointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED_BY_PROVIDER);
        assertThat(savedAppointment.getRejectionReason())
                .isEqualTo("Please choose another available time");
    }

    @Test
    @DisplayName("Should delete client-cancelled appointment after provider acknowledgement")
    void shouldDeleteClientCancelledAppointmentAfterProviderAcknowledgement() throws Exception {
        User provider = createUser("provider-client-cancel-ack", UserRole.PROVIDER);
        User client = createUser("client-client-cancel-ack", UserRole.CLIENT);
        linkClient(provider, client);

        Offering offering = createOffering(provider);
        Appointment appointment = createAppointment(provider, client, offering);
        appointment.confirm();
        appointment.cancelByClient();
        appointmentRepository.save(appointment);

        MockHttpSession providerSession = authenticatedSession(provider);

        mockMvc.perform(post("/provider/appointments/{appointmentId}/acknowledge", appointment.getId()).with(csrf())
                        .session(providerSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider?section=provider-calendar"));

        assertThat(appointmentRepository.findById(appointment.getId())).isEmpty();
    }
    
    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(
                    Instant.parse("2026-06-01T06:00:00Z"),
                    ZoneOffset.UTC
            );
        }
    }
}