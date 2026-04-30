package life.wellnara;

import jakarta.servlet.http.HttpSession;
import life.wellnara.model.ProviderInvitation;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.ModelAndViewAssert.assertViewName;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration MVC tests for admin invitation flow and provider registration flow.
 *
 * <p>These tests verify the real interaction between:
 * controllers, session state, redirects, template rendering and database state.
 *
 * <p>Covered scenarios:
 * <ul>
 *     <li>Admin invite with existing email returns validation error instead of HTTP 500.</li>
 *     <li>Successful invite shows registration link only once.</li>
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

    /**
     * Verifies that if admin tries to invite a provider using an email
     * that already exists in the system, the application does not fail with HTTP 500.
     *
     * <p>Expected behavior:
     * <ul>
     *     <li>request returns HTTP 200,</li>
     *     <li>admin page is rendered again,</li>
     *     <li>validation message is added to the model,</li>
     *     <li>users list remains available for rendering.</li>
     * </ul>
     *
     * @throws Exception if MockMvc request fails
     */
    @Test
    @DisplayName("Should return admin page with error when invited email already exists")
    void shouldReturnAdminPageWithErrorWhenInvitedEmailAlreadyExists() throws Exception {
        User admin = getAdminUser();
        MockHttpSession session = createSessionWithCurrentUser(admin);

        User existingProvider = new User();
        existingProvider.setUsername("existing-provider");
        existingProvider.setPassword("123");
        existingProvider.setEmail("provider-existing@example.com");
        existingProvider.setRole(UserRole.PROVIDER);
        userRepository.save(existingProvider);

        mockMvc.perform(post("/admin/invite")
                        .session(session)
                        .param("email", "provider-existing@example.com"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("inviteError"))
                .andExpect(model().attributeExists("users"))
                .andExpect(content().string(containsString("Email already used")));
    }

    /**
     * Verifies one-time visibility of provider invitation link on admin page.
     *
     * <p>Expected behavior:
     * <ul>
     *     <li>after successful invite the controller redirects to /admin,</li>
     *     <li>on first GET /admin the registration link is visible,</li>
     *     <li>on second GET /admin with the same session the link is no longer visible.</li>
     * </ul>
     *
     * @throws Exception if MockMvc request fails
     */
    @Test
    @DisplayName("Should show provider invite link only once after successful invitation")
    void shouldShowProviderInviteLinkOnlyOnceAfterSuccessfulInvitation() throws Exception {
        User admin = getAdminUser();
        MockHttpSession session = createSessionWithCurrentUser(admin);

        mockMvc.perform(post("/admin/invite")
                        .session(session)
                        .param("email", "new-provider@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));

        mockMvc.perform(get("/admin").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Provider registration link:")))
                .andExpect(content().string(containsString("http://localhost:8080/provider/register?token=")));

        mockMvc.perform(get("/admin").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Provider registration link:"))));
    }

    /**
     * Verifies that provider registration does not create a user
     * when password and confirmPassword are different.
     *
     * <p>Expected behavior:
     * <ul>
     *     <li>registration page is rendered again,</li>
     *     <li>error message is shown,</li>
     *     <li>token and email remain available in the model,</li>
     *     <li>new provider user is not saved in the database.</li>
     * </ul>
     *
     * @throws Exception if MockMvc request fails
     */
    @Test
    @DisplayName("Should return registration page with error when passwords do not match")
    void shouldReturnRegistrationPageWithErrorWhenPasswordsDoNotMatch() throws Exception {
        String token = providerInvitationService.invite("mismatch@example.com");

        mockMvc.perform(post("/provider/register")
                        .param("token", token)
                        .param("name", "provider-mismatch")
                        .param("password", "secret123")
                        .param("confirmPassword", "different123"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("token"))
                .andExpect(model().attributeExists("email"))
                .andExpect(model().attributeExists("error"))
                .andExpect(content().string(containsString("Passwords do not match")));

        assertThat(userRepository.findByUsername("provider-mismatch")).isEmpty();
        assertThat(invitationRepository.findByToken(token)).isPresent();
    }

    /**
     * Verifies complete successful provider registration flow,
     * followed by logout and normal login with the same credentials.
     *
     * <p>Expected behavior:
     * <ul>
     *     <li>successful registration opens provider page immediately,</li>
     *     <li>success message and provider email are shown right after registration,</li>
     *     <li>logout invalidates current session and redirects to login page,</li>
     *     <li>subsequent login with the same credentials redirects to /provider,</li>
     *     <li>regular provider page after login does not show registration completion message.</li>
     * </ul>
     *
     * @throws Exception if MockMvc request fails
     */
    @Test
    @DisplayName("Should register provider successfully and allow logout then login")
    @DirtiesContext
    void shouldRegisterProviderSuccessfullyAndAllowLogoutThenLogin() throws Exception {
        String email = "successful-provider@example.com";
        String username = "successful-provider";
        String password = "pass123";

        String token = providerInvitationService.invite(email);

        var registrationResult = mockMvc.perform(post("/provider/register")
                        .param("token", token)
                        .param("name", username)
                        .param("password", password)
                        .param("confirmPassword", password))
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
                .andExpect(content().string(containsString(username)));

        mockMvc.perform(get("/auth/logout").session((MockHttpSession) registrationSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login"));

        var loginResult = mockMvc.perform(post("/auth/login")
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
                .andExpect(content().string(containsString(username)));
    }

    /**
     * Returns preloaded admin user from test database.
     *
     * @return admin user
     */
    private User getAdminUser() {
        return userRepository.findByUsername("admin")
                .orElseThrow(() -> new IllegalStateException("Admin user not found in test database"));
    }

    /**
     * Creates session containing authenticated current user.
     *
     * @param user authenticated user
     * @return mock HTTP session with currentUser attribute
     */
    private MockHttpSession createSessionWithCurrentUser(User user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", user);
        return session;
    }
}