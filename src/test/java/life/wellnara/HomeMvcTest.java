package life.wellnara;

import life.wellnara.model.User;
import life.wellnara.model.UserRole;
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
                .andExpect(content().string(containsString("Appointment requests")))
                .andExpect(content().string(containsString("Wallet")));
    }

    @Test
    @DisplayName("Should redirect client from home to client landing")
    void shouldRedirectClientHomeToClientLanding() throws Exception {
        User client = createUser("home-client", "home-client@example.com", UserRole.CLIENT);
        MockHttpSession session = authenticatedSession(client);

        mockMvc.perform(get("/home").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/client"));
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
