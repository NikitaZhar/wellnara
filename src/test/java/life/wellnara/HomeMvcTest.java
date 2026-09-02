package life.wellnara;

import life.wellnara.model.Appointment;
import life.wellnara.model.Offering;
import life.wellnara.model.ProviderClientLink;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.AppointmentRepository;
import life.wellnara.repository.OfferingRepository;
import life.wellnara.repository.ProviderClientLinkRepository;
import life.wellnara.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static life.wellnara.SecurityTestSupport.authenticatedSession;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MVC tests for the Home landing page.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HomeMvcTest {

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

    @MockBean
    private JavaMailSender mailSender;

    @Test
    @DisplayName("Should render provider home for authenticated provider")
    void shouldRenderProviderHome() throws Exception {
        User provider = createUser("home-provider", "home-provider@example.com", UserRole.PROVIDER);
        MockHttpSession session = authenticatedSession(provider);

        mockMvc.perform(get("/home").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("provider-home"))
                // UI is localized; the default locale is Russian.
                .andExpect(content().string(containsString("Смотреть заявки")));
    }

    @Test
    @DisplayName("Should render client home for authenticated client")
    void shouldRenderClientHome() throws Exception {
        User client = createUser("home-client", "home-client@example.com", UserRole.CLIENT);
        MockHttpSession session = authenticatedSession(client);

        mockMvc.perform(get("/home").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("client-home"))
                // UI is localized; the default locale is Russian.
                .andExpect(content().string(containsString("Ближайшие записи")))
                // The wallet card is now titled "Счёт" and links to the account page.
                .andExpect(content().string(containsString("Счёт")))
                .andExpect(content().string(containsString("/client/wallet")));
    }

    @Test
    @DisplayName("A completed appointment does not appear in the client's Home requests panel")
    void completedAppointmentAbsentFromHomeRequests() throws Exception {
        User provider = createUser("home-prov-comp", "home-prov-comp@example.com", UserRole.PROVIDER);
        User client = createUser("home-client-comp", "home-client-comp@example.com", UserRole.CLIENT);
        providerClientLinkRepository.save(new ProviderClientLink(provider, client, LocalDateTime.now()));
        Offering offering = offeringRepository.save(
                new Offering(provider, "Массаж", "desc", new BigDecimal("50.00"), 60));

        Appointment appointment = new Appointment(provider, client, offering, LocalDateTime.of(2026, 1, 1, 10, 0));
        appointment.schedule();
        appointment.complete();
        appointmentRepository.save(appointment);

        mockMvc.perform(get("/home").session(authenticatedSession(client)))
                .andExpect(status().isOk())
                .andExpect(view().name("client-home"))
                // The "My requests" panel shows its empty state: a completed term is not a request/update.
                .andExpect(content().string(containsString("Нет заявок и обновлений")));
    }

    @Test
    @DisplayName("Should redirect unauthenticated home request to login")
    void shouldRedirectUnauthenticatedHomeToLogin() throws Exception {
        mockMvc.perform(get("/home"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login"));
    }

    private User createUser(String username, String email, UserRole role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("123");
        user.setEmail(email);
        user.setRole(role);
        return userRepository.save(user);
    }
}
