package life.wellnara;

import life.wellnara.service.LoginAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration MVC test for the login brute-force guard (step 1.6).
 *
 * <p>Uses a key that does not correspond to any real user, so the lockout is
 * exercised through the failed-authentication path without depending on admin
 * credentials or interfering with other tests. The test-profile threshold is
 * {@code wellnara.login.max-attempts=3}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LoginBruteForceMvcTest {

    private static final String TARGET_USERNAME = "brute-force-target";
    private static final int MAX_ATTEMPTS = 3;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void clearGuardState() {
        // The guard is a process-wide singleton (not rolled back by @Transactional),
        // so make this test independent of any earlier failures for the key.
        loginAttemptService.reset(TARGET_USERNAME);
    }

    @Test
    @DisplayName("Should keep reporting invalid credentials until the attempt threshold is reached")
    void shouldReportInvalidCredentialsBeforeLockout() throws Exception {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            performLogin("wrong-password")
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Неверный логин или пароль")));
        }
    }

    @Test
    @DisplayName("Should lock the login out after too many failed attempts")
    void shouldLockOutAfterTooManyFailedAttempts() throws Exception {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            performLogin("wrong-password");
        }

        performLogin("wrong-password")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Слишком много неудачных попыток входа")));
    }

    private org.springframework.test.web.servlet.ResultActions performLogin(String password) throws Exception {
        return mockMvc.perform(post("/auth/login").with(csrf())
                .param("username", TARGET_USERNAME)
                .param("password", password));
    }
}
