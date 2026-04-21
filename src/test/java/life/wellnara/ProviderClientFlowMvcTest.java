package life.wellnara;

import jakarta.servlet.http.HttpSession;
import life.wellnara.model.ClientInvitation;
import life.wellnara.model.ProviderClientLink;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.ClientInvitationRepository;
import life.wellnara.repository.ProviderClientLinkRepository;
import life.wellnara.repository.UserRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration MVC tests for complete provider-client flow.
 *
 * <p>These tests verify the interaction between controllers, session state,
 * redirects, template rendering and database state for the following chain:
 *
 * <ul>
 *     <li>provider logs in,</li>
 *     <li>provider invites client,</li>
 *     <li>client registers by invitation token,</li>
 *     <li>provider sees registered client,</li>
 *     <li>provider can delete client,</li>
 *     <li>client can log out and log in again after successful registration.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProviderClientFlowMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClientInvitationRepository clientInvitationRepository;

    @Autowired
    private ProviderClientLinkRepository providerClientLinkRepository;

    /**
     * Verifies that provider can create client invitation successfully and
     * the registration link is shown only once on provider page.
     *
     * @throws Exception if MockMvc request fails
     */
    @Test
    @DisplayName("Should invite client successfully and show invite link only once")
    void shouldInviteClientSuccessfullyAndShowInviteLinkOnlyOnce() throws Exception {
        User provider = createProvider("provider-one", "provider-one@example.com", "123");
        MockHttpSession session = createSessionWithCurrentUser(provider);

        mockMvc.perform(post("/provider/invite-client")
                        .session(session)
                        .param("email", "client-one@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider"));

        mockMvc.perform(get("/provider").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Client registration link:")))
                .andExpect(content().string(containsString("http://localhost:8080/client/register?token=")));

        mockMvc.perform(get("/provider").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Client registration link:"))));

        assertThat(clientInvitationRepository.existsByEmail("client-one@example.com")).isTrue();
    }

    /**
     * Verifies that client registration with mismatched passwords
     * returns user back to registration page and does not create client.
     *
     * @throws Exception if MockMvc request fails
     */
    @Test
    @DisplayName("Should return client registration page with error when passwords do not match")
    void shouldReturnClientRegistrationPageWithErrorWhenPasswordsDoNotMatch() throws Exception {
        User provider = createProvider("provider-two", "provider-two@example.com", "123");
        ClientInvitation invitation = createClientInvitation(provider, "client-two@example.com");

        mockMvc.perform(post("/client/register")
                        .param("token", invitation.getToken())
                        .param("name", "client-two")
                        .param("password", "secret123")
                        .param("confirmPassword", "different123"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("token"))
                .andExpect(model().attributeExists("email"))
                .andExpect(model().attributeExists("error"))
                .andExpect(content().string(containsString("Passwords do not match")));

        assertThat(userRepository.findByUsername("client-two")).isEmpty();
        assertThat(clientInvitationRepository.findByToken(invitation.getToken())).isPresent();
        assertThat(providerClientLinkRepository.findAllByProvider(provider)).isEmpty();
    }

    /**
     * Verifies complete successful flow:
     * provider invites client, client registers, provider sees the client,
     * client logs out, client logs in again, provider deletes the client.
     *
     * @throws Exception if MockMvc request fails
     */
    @Test
    @DisplayName("Should complete full provider-client flow successfully")
    @DirtiesContext
    void shouldCompleteFullProviderClientFlowSuccessfully() throws Exception {
        User provider = createProvider("provider-three", "provider-three@example.com", "123");
        MockHttpSession providerSession = createSessionWithCurrentUser(provider);

        mockMvc.perform(post("/provider/invite-client")
                        .session(providerSession)
                        .param("email", "client-three@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider"));

        ClientInvitation invitation = clientInvitationRepository.findByToken(
                        extractTokenFromInvitationEmail("client-three@example.com"))
                .orElseThrow(() -> new IllegalStateException("Client invitation not found"));

        var registrationResult = mockMvc.perform(post("/client/register")
                        .param("token", invitation.getToken())
                        .param("name", "client-three")
                        .param("password", "pass123")
                        .param("confirmPassword", "pass123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Registration completed successfully")))
                .andExpect(content().string(containsString("client-three@example.com")))
                .andReturn();

        HttpSession clientSession = registrationResult.getRequest().getSession(false);
        assertThat(clientSession).isNotNull();

        User savedClient = userRepository.findByUsername("client-three")
                .orElseThrow(() -> new IllegalStateException("Registered client not found"));

        assertThat(savedClient.getRole()).isEqualTo(UserRole.CLIENT);
        assertThat(savedClient.getEmail()).isEqualTo("client-three@example.com");
        assertThat(clientInvitationRepository.findByToken(invitation.getToken())).isEmpty();

        Optional<ProviderClientLink> providerClientLink = providerClientLinkRepository
                .findByProviderAndClientId(provider, savedClient.getId());

        assertThat(providerClientLink).isPresent();
        assertThat(providerClientLink.get().getProvider().getId()).isEqualTo(provider.getId());
        assertThat(providerClientLink.get().getClient().getId()).isEqualTo(savedClient.getId());

        mockMvc.perform(get("/provider").session(providerSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("My clients")))
                .andExpect(content().string(containsString("client-three")))
                .andExpect(content().string(containsString("client-three@example.com")));

        mockMvc.perform(get("/auth/logout").session((MockHttpSession) clientSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login"));

        var loginResult = mockMvc.perform(post("/auth/login")
                        .param("username", "client-three")
                        .param("password", "pass123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/client"))
                .andReturn();

        HttpSession loggedClientSession = loginResult.getRequest().getSession(false);
        assertThat(loggedClientSession).isNotNull();

        mockMvc.perform(get("/client").session((MockHttpSession) loggedClientSession))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Registration completed successfully"))))
                .andExpect(content().string(not(containsString("client-three@example.com"))));

        mockMvc.perform(post("/provider/clients/{clientId}/delete", savedClient.getId())
                        .session(providerSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider"));

        assertThat(userRepository.findById(savedClient.getId())).isEmpty();
        assertThat(providerClientLinkRepository.findByProviderAndClientId(provider, savedClient.getId())).isEmpty();

        mockMvc.perform(get("/provider").session(providerSession))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("client-three@example.com"))));
    }

    /**
     * Verifies that provider cannot invite client with email that already exists in the system.
     *
     * @throws Exception if MockMvc request fails
     */
    @Test
    @DisplayName("Should return provider page with error when client email already exists")
    void shouldReturnProviderPageWithErrorWhenClientEmailAlreadyExists() throws Exception {
        User provider = createProvider("provider-four", "provider-four@example.com", "123");
        MockHttpSession providerSession = createSessionWithCurrentUser(provider);

        User existingClient = new User();
        existingClient.setUsername("existing-client");
        existingClient.setPassword("123");
        existingClient.setEmail("existing-client@example.com");
        existingClient.setRole(UserRole.CLIENT);
        userRepository.save(existingClient);

        mockMvc.perform(post("/provider/invite-client")
                        .session(providerSession)
                        .param("email", "existing-client@example.com"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("clientInviteError"))
                .andExpect(content().string(containsString("Email already used")));
    }

    /**
     * Creates provider user for test scenario.
     *
     * @param username provider username
     * @param email provider email
     * @param password provider password
     * @return saved provider user
     */
    private User createProvider(String username, String email, String password) {
        User provider = new User();
        provider.setUsername(username);
        provider.setPassword(password);
        provider.setEmail(email);
        provider.setRole(UserRole.PROVIDER);
        return userRepository.save(provider);
    }

    /**
     * Creates client invitation for test scenario.
     *
     * @param provider provider user
     * @param email invited client email
     * @return saved client invitation
     */
    private ClientInvitation createClientInvitation(User provider, String email) {
        ClientInvitation invitation = new ClientInvitation(provider, email);
        return clientInvitationRepository.save(invitation);
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

    /**
     * Returns invitation token for email from repository.
     *
     * @param email invited client email
     * @return invitation token
     */
    private String extractTokenFromInvitationEmail(String email) {
        return clientInvitationRepository.findAll().stream()
                .filter(invitation -> invitation.getEmail().equals(email))
                .findFirst()
                .map(ClientInvitation::getToken)
                .orElseThrow(() -> new IllegalStateException("Invitation token not found"));
    }
}