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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
 * Integration MVC tests for the complete provider-client flow.
 *
 * <p>Email delivery is replaced by an inert {@link JavaMailSender} so these tests never reach a
 * real SMTP provider; they assert application flow and link visibility. The construction and
 * dispatch of invitation emails is verified separately in
 * {@code life.wellnara.service.email.InvitationNotificationServiceTest}.
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

    @MockBean
    private JavaMailSender mailSender;

    @Test
    @DisplayName("Should invite client successfully and show confirmation only once")
    void shouldInviteClientSuccessfullyAndShowConfirmationOnlyOnce() throws Exception {
        User provider = createProvider("provider-one", "provider-one@example.com", "123");
        MockHttpSession session = createSessionWithCurrentUser(provider);

        mockMvc.perform(post("/provider/invite-client")
                        .session(session)
                        .param("email", "client-one@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider"));

        mockMvc.perform(get("/provider").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Приглашение отправлено на client-one@example.com")));

        mockMvc.perform(get("/provider").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Приглашение отправлено на"))));

        assertThat(clientInvitationRepository.existsByEmail("client-one@example.com")).isTrue();
    }

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

        MvcResult registrationResult = mockMvc.perform(post("/client/register")
                .param("token", invitation.getToken())
                .param("name", "client-three")
                .param("password", "pass123")
                .param("confirmPassword", "pass123"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/client"))
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

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .param("username", "client-three")
                        .param("password", "pass123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/client"))
                .andReturn();

        HttpSession loggedClientSession = loginResult.getRequest().getSession(false);
        assertThat(loggedClientSession).isNotNull();

        mockMvc.perform(get("/client").session((MockHttpSession) loggedClientSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Wellnara Client")));

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

    @Test
    @DisplayName("Should return provider page with error when client email already exists")
    void shouldReturnProviderPageWithErrorWhenClientEmailAlreadyExists() throws Exception {
        User provider = createProvider("provider-four", "provider-four@example.com", "123");
        MockHttpSession providerSession = createSessionWithCurrentUser(provider);

        User existingClient = createClient("existing-client", "existing-client@example.com", "123");

        assertThat(existingClient.getRole()).isEqualTo(UserRole.CLIENT);

        var result = mockMvc.perform(post("/provider/invite-client")
                        .session(providerSession)
                        .param("email", "existing-client@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider"))
                .andReturn();

        MockHttpSession updatedSession =
                (MockHttpSession) result.getRequest().getSession(false);

        assertThat(updatedSession).isNotNull();

        mockMvc.perform(get("/provider").session(updatedSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Email already used")));
    }

    @Test
    @DisplayName("Should delete linked client through admin page")
    void shouldDeleteLinkedClientThroughAdminPage() throws Exception {
        User provider = createProvider(
                "provider-admin-delete-client",
                "provider-admin-delete-client@example.com",
                "123"
        );
        User client = createClient(
                "client-admin-delete",
                "client-admin-delete@example.com",
                "123"
        );

        linkClient(provider, client);

        MockHttpSession adminSession = loginAs("admin", "#admin@", "/admin");

        mockMvc.perform(post("/admin/users/{id}/delete", client.getId())
                        .session(adminSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));

        assertThat(userRepository.findById(client.getId())).isEmpty();
        assertThat(providerClientLinkRepository.findByProviderAndClientId(provider, client.getId())).isEmpty();
        assertThat(userRepository.findById(provider.getId())).isPresent();
    }

    @Test
    @DisplayName("Should delete linked provider through admin page")
    void shouldDeleteLinkedProviderThroughAdminPage() throws Exception {
        User provider = createProvider(
                "provider-admin-delete-provider",
                "provider-admin-delete-provider@example.com",
                "123"
        );
        User client = createClient(
                "client-of-deleted-provider",
                "client-of-deleted-provider@example.com",
                "123"
        );

        linkClient(provider, client);

        MockHttpSession adminSession = loginAs("admin", "#admin@", "/admin");

        mockMvc.perform(post("/admin/users/{id}/delete", provider.getId())
                        .session(adminSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));

        assertThat(userRepository.findById(provider.getId())).isEmpty();
        assertThat(userRepository.findById(client.getId())).isPresent();

        assertThat(providerClientLinkRepository.findAll())
                .noneMatch(link -> link.getProvider().getId().equals(provider.getId()));
    }

    private User createProvider(String username, String email, String password) {
        User provider = new User();
        provider.setUsername(username);
        provider.setPassword(password);
        provider.setEmail(email);
        provider.setRole(UserRole.PROVIDER);
        return userRepository.save(provider);
    }

    private User createClient(String username, String email, String password) {
        User client = new User();
        client.setUsername(username);
        client.setPassword(password);
        client.setEmail(email);
        client.setRole(UserRole.CLIENT);
        return userRepository.save(client);
    }

    private ClientInvitation createClientInvitation(User provider, String email) {
        LocalDateTime now = LocalDateTime.now();
        return clientInvitationRepository.save(
                new ClientInvitation(provider, email, now, now.plusDays(7)));
    }

    private ProviderClientLink linkClient(User provider, User client) {
        ProviderClientLink link = new ProviderClientLink(provider, client, LocalDateTime.now());
        return providerClientLinkRepository.save(link);
    }

    private MockHttpSession createSessionWithCurrentUser(User user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", user);
        return session;
    }

    private String extractTokenFromInvitationEmail(String email) {
        return clientInvitationRepository.findAll().stream()
                .filter(invitation -> invitation.getEmail().equals(email))
                .findFirst()
                .map(ClientInvitation::getToken)
                .orElseThrow(() -> new IllegalStateException("Invitation token not found"));
    }
    
    private MockHttpSession loginAs(String username,
                                    String password,
                                    String expectedRedirectUrl) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(expectedRedirectUrl))
                .andReturn();

        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
