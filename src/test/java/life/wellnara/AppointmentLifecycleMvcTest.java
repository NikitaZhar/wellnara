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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MVC tests for provider appointment actions and client acknowledgement flow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
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

        MockHttpSession providerSession = createSessionWithCurrentUser(provider);

        mockMvc.perform(post("/provider/appointments/{appointmentId}/reject", appointment.getId())
                        .session(providerSession)
                        .param("rejectionReason", "Requested time is not suitable"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider"));

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

        MockHttpSession clientSession = createSessionWithCurrentUser(client);

        mockMvc.perform(post("/client/appointments/{appointmentId}/acknowledge", appointment.getId())
                        .session(clientSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/client"));

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

        MockHttpSession providerSession = createSessionWithCurrentUser(provider);

        mockMvc.perform(post("/provider/appointments/{appointmentId}/request-payment", appointment.getId())
                        .session(providerSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider"));

        Appointment savedAppointment = appointmentRepository.findById(appointment.getId())
                .orElseThrow();

        assertThat(savedAppointment.getStatus()).isEqualTo(AppointmentStatus.PAYMENT_REQUESTED);

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
                LocalDateTime.of(2026, 5, 4, 8, 0)
        );

        return appointmentRepository.save(appointment);
    }

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

    private void linkClient(User provider, User client) {
        providerClientLinkRepository.save(
                new ProviderClientLink(provider, client, LocalDateTime.now())
        );
    }

    private MockHttpSession createSessionWithCurrentUser(User user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", user);
        return session;
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

        MockHttpSession clientSession = createSessionWithCurrentUser(client);

        mockMvc.perform(post("/client/appointments/{appointmentId}/pay", appointment.getId())
                        .session(clientSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/client"));

        Appointment savedAppointment = appointmentRepository.findById(appointment.getId())
                .orElseThrow();

        assertThat(savedAppointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
    }
}