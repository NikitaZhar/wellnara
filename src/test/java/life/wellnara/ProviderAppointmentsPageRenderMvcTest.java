package life.wellnara;

import life.wellnara.model.Appointment;
import life.wellnara.model.CalendarProvider;
import life.wellnara.model.Offering;
import life.wellnara.model.ProviderClientLink;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.AppointmentRepository;
import life.wellnara.repository.OfferingRepository;
import life.wellnara.repository.ProviderClientLinkRepository;
import life.wellnara.repository.UserRepository;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static life.wellnara.SecurityTestSupport.authenticatedSession;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Render test for the provider appointments page. Guards the whole GET path
 * (query + calendar links + Thymeleaf render), which was previously untested and
 * let a template expression break the page while the Home widget stayed fine.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProviderAppointmentsPageRenderMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OfferingRepository offeringRepository;

    @Autowired
    private ProviderClientLinkRepository providerClientLinkRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Test
    @DisplayName("Provider appointments page renders a scheduled appointment when the preferred calendar is Google")
    void rendersScheduledAppointmentWithGoogleCalendar() throws Exception {
        User provider = createUser("appt-render-provider", UserRole.PROVIDER, CalendarProvider.GOOGLE);
        User client = createUser("appt-render-client", UserRole.CLIENT, null);
        providerClientLinkRepository.save(new ProviderClientLink(provider, client, LocalDateTime.now()));

        Offering offering = offeringRepository.save(
                new Offering(provider, "Consultation", "A session", new BigDecimal("45.00"), 60));
        offering.setCurrency("EUR");
        offeringRepository.save(offering);

        Appointment appointment = new Appointment(
                provider, client, offering, LocalDateTime.of(2026, 6, 1, 10, 0));
        appointment.schedule();
        appointmentRepository.save(appointment);

        // The page must render (previously it threw on the GOOGLE branch), and the
        // Google add-event link must be present. Asserted on the URL host so the
        // check is locale-independent (button label is translated).
        mockMvc.perform(get("/provider/appointments").session(authenticatedSession(provider)))
                .andExpect(status().isOk())
                .andExpect(view().name("provider-appointments"))
                .andExpect(model().attribute("confirmedAppointments", Matchers.hasSize(1)))
                .andExpect(content().string(containsString("calendar.google.com")));
    }

    private User createUser(String usernamePrefix, UserRole role, CalendarProvider calendar) {
        String username = usernamePrefix + "-" + System.nanoTime();

        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@test.com");
        user.setPassword("123");
        user.setRole(role);
        user.setCurrency("EUR");
        user.setPreferredCalendar(calendar);

        return userRepository.save(user);
    }
}
