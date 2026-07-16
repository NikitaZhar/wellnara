package life.wellnara;

import jakarta.servlet.http.HttpSession;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.ProviderInvitationRepository;
import life.wellnara.repository.UserRepository;
import life.wellnara.service.ProviderInvitationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static life.wellnara.SecurityTestSupport.authenticatedSession;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration MVC tests for the admin invitation flow and provider registration flow.
 *
 * <p>Email delivery is replaced by an inert {@link JavaMailSender} so these tests never reach a
 * real SMTP provider. The construction and dispatch of invitation emails is verified separately in
 * {@code life.wellnara.service.email.InvitationNotificationServiceTest}.
 *
 * <p>Covered scenarios:
 * <ul>
 *     <li>Admin invite with existing email returns validation error instead of HTTP 500.</li>
 *     <li>Successful invite shows a confirmation message only once.</li>
 *     <li>Provider registration with mismatched passwords returns user to registration page.</li>
 *     <li>Successful provider registration is followed by logout and normal login flow.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProviderAdminFlowMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProviderInvitationRepository invitationRepository;

    @Autowired
    private ProviderInvitationService providerInvitationService;

    @MockBean
    private JavaMailSender mailSender;

    @Test
    @DisplayName("Should return admin page with error when invited email already exists")
    void shouldReturnAdminPageWithErrorWhenInvitedEmailAlreadyExists() throws Exception {
        User admin = getAdminUser();
        MockHttpSession session = authenticatedSession(admin);

        User existingProvider = new User();
        existingProvider.setUsername("existing-provider");
        existingProvider.setPassword("123");
        existingProvider.setEmail("provider-existing@example.com");
        existingProvider.setRole(UserRole.PROVIDER);
        userRepository.save(existingProvider);

        mockMvc.perform(post("/admin/invite").with(csrf())
                        .session(session)
                        .param("email", "provider-existing@example.com"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("inviteError"))
                .andExpect(model().attributeExists("users"))
                .andExpect(content().string(containsString("Email already used")));
    }

    @Test
    @DisplayName("Should show provider invitation confirmation only once after successful invitation")
    void shouldShowProviderInvitationConfirmationOnlyOnceAfterSuccessfulInvitation() throws Exception {
        User admin = getAdminUser();
        MockHttpSession session = authenticatedSession(admin);

        mockMvc.perform(post("/admin/invite").with(csrf())
                        .session(session)
                        .param("email", "new-provider@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));

        mockMvc.perform(get("/admin").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Provider invitation was sent to new-provider@example.com")));

        mockMvc.perform(get("/admin").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Provider invitation was sent to"))));
    }

    @Test
    @DisplayName("Should return registration page with error when passwords do not match")
    void shouldReturnRegistrationPageWithErrorWhenPasswordsDoNotMatch() throws Exception {
        String token = providerInvitationService.invite("mismatch@example.com");

        mockMvc.perform(post("/provider/register").with(csrf())
                        .param("token", token)
                        .param("name", "provider-mismatch")
                        .param("password", "secret123")
                        .param("confirmPassword", "different123")
                        .param("firstName", "Pro")
                        .param("lastName", "Vider"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("token"))
                .andExpect(model().attributeExists("email"))
                .andExpect(model().attributeExists("error"))
                .andExpect(content().string(containsString("Passwords do not match")));

        assertThat(userRepository.findByUsername("provider-mismatch")).isEmpty();
        assertThat(invitationRepository.findByToken(token)).isPresent();
    }

    @Test
    @DisplayName("Should register provider successfully and allow logout then login")
    @DirtiesContext
    void shouldRegisterProviderSuccessfullyAndAllowLogoutThenLogin() throws Exception {
        String email = "successful-provider@example.com";
        String username = "successful-provider";
        String password = "pass123";

        String token = providerInvitationService.invite(email);

        var registrationResult = mockMvc.perform(post("/provider/register").with(csrf())
                        .param("token", token)
                        .param("name", username)
                        .param("password", password)
                        .param("confirmPassword", password)
                        .param("firstName", "Successful")
                        .param("lastName", "Provider"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider"))
                .andReturn();

        HttpSession registrationSession = registrationResult.getRequest().getSession(false);
        assertThat(registrationSession).isNotNull();

        Optional<User> savedUser = userRepository.findByUsername(username);
        assertThat(savedUser).isPresent();
        assertThat(savedUser.get().getEmail()).isEqualTo(email);
        assertThat(savedUser.get().getRole()).isEqualTo(UserRole.PROVIDER);

        mockMvc.perform(get("/provider").session((MockHttpSession) registrationSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Wellnara: Provider")))
                .andExpect(content().string(containsString("Successful Provider")));

        mockMvc.perform(get("/auth/logout").session((MockHttpSession) registrationSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login"));

        var loginResult = mockMvc.perform(post("/auth/login").with(csrf())
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider"))
                .andReturn();

        HttpSession loginSession = loginResult.getRequest().getSession(false);
        assertThat(loginSession).isNotNull();

        mockMvc.perform(get("/provider").session((MockHttpSession) loginSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Wellnara: Provider")))
                .andExpect(content().string(containsString("Successful Provider")));
    }

    private User getAdminUser() {
        return userRepository.findByUsername("admin")
                .orElseThrow(() -> new IllegalStateException("Admin user not found in test database"));
    }

}

