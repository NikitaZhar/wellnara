package life.wellnara;

import life.wellnara.model.PasswordResetToken;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.PasswordResetTokenRepository;
import life.wellnara.repository.UserRepository;
import life.wellnara.service.PasswordResetService;
import life.wellnara.service.email.EmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests for the "forgot password" flow: anti-enumeration, single-use and expiring
 * tokens, the request cool-down, and the password policy on reset. {@link EmailService}
 * is mocked so no SMTP is touched and the emailed link's token can be captured. A
 * mutable {@link Clock} lets expiry and cool-down be exercised deterministically.
 */
@SpringBootTest
@Transactional
@Import(PasswordResetServiceTest.MutableClockConfig.class)
class PasswordResetServiceTest {

    private static final Pattern TOKEN_IN_LINK = Pattern.compile("/auth/reset-password\\?token=(\\S+)");

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    @MockBean
    private EmailService emailService;

    @Test
    @DisplayName("An unknown email produces no token and sends no email")
    void unknownEmailDoesNothing() {
        passwordResetService.requestReset("nobody@example.com");

        assertThat(tokenRepository.findAll()).isEmpty();
        verify(emailService, never()).sendPlainTextEmail(any(), any(), any());
    }

    @Test
    @DisplayName("A known email stores only the token hash and emails the raw link")
    void knownEmailStoresHashedTokenAndEmailsLink() {
        User user = createUser("known@example.com", "current-pass");

        passwordResetService.requestReset("known@example.com");

        String rawToken = captureEmailedToken();
        List<PasswordResetToken> tokens = tokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getUser().getId()).isEqualTo(user.getId());
        assertThat(tokens.get(0).getTokenHash()).isNotEqualTo(rawToken); // stored hashed, not raw
        assertThat(passwordResetService.isTokenUsable(rawToken)).isTrue();
    }

    @Test
    @DisplayName("A second request within the cool-down sends no further email")
    void cooldownSuppressesSecondEmail() {
        createUser("cool@example.com", "current-pass");

        passwordResetService.requestReset("cool@example.com");
        passwordResetService.requestReset("cool@example.com");

        verify(emailService).sendPlainTextEmail(eq("cool@example.com"), any(), any()); // exactly once
        assertThat(tokenRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("A valid token sets the new password and cannot be reused")
    void validTokenResetsPasswordAndIsSingleUse() {
        User user = createUser("reset@example.com", "old-password");
        passwordResetService.requestReset("reset@example.com");
        String rawToken = captureEmailedToken();

        passwordResetService.resetPassword(rawToken, "brand-new-pass", "brand-new-pass");

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("brand-new-pass", reloaded.getPassword())).isTrue();
        assertThat(passwordResetService.isTokenUsable(rawToken)).isFalse();
        assertThatThrownBy(() -> passwordResetService.resetPassword(rawToken, "another-pass", "another-pass"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Mismatched passwords are rejected and the token stays usable")
    void mismatchedPasswordsRejectedAndTokenKept() {
        createUser("mismatch@example.com", "old-password");
        passwordResetService.requestReset("mismatch@example.com");
        String rawToken = captureEmailedToken();

        assertThatThrownBy(() -> passwordResetService.resetPassword(rawToken, "brand-new-pass", "different-pass"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("do not match");
        assertThat(passwordResetService.isTokenUsable(rawToken)).isTrue();
    }

    @Test
    @DisplayName("A new password shorter than the policy minimum is rejected and the token stays usable")
    void shortPasswordRejected() {
        createUser("short@example.com", "old-password");
        passwordResetService.requestReset("short@example.com");
        String rawToken = captureEmailedToken();

        assertThatThrownBy(() -> passwordResetService.resetPassword(rawToken, "short", "short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least");
        assertThat(passwordResetService.isTokenUsable(rawToken)).isTrue();
    }

    @Test
    @DisplayName("An unknown token is rejected")
    void invalidTokenRejected() {
        assertThat(passwordResetService.isTokenUsable("not-a-real-token")).isFalse();
        assertThatThrownBy(() -> passwordResetService.resetPassword("not-a-real-token", "brand-new-pass", "brand-new-pass"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("A token past its lifetime is no longer usable")
    void expiredTokenNotUsable() {
        createUser("expire@example.com", "old-password");
        passwordResetService.requestReset("expire@example.com");
        String rawToken = captureEmailedToken();

        mutableClock().advanceMinutes(61); // default TTL is 60 minutes

        assertThat(passwordResetService.isTokenUsable(rawToken)).isFalse();
        assertThatThrownBy(() -> passwordResetService.resetPassword(rawToken, "brand-new-pass", "brand-new-pass"))
                .isInstanceOf(IllegalStateException.class);
    }

    private User createUser(String email, String rawPassword) {
        User user = new User();
        user.setUsername("user-" + email);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(UserRole.CLIENT);
        return userRepository.save(user);
    }

    private String captureEmailedToken() {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPlainTextEmail(any(), any(), body.capture());
        Matcher matcher = TOKEN_IN_LINK.matcher(body.getValue());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private MutableClock mutableClock() {
        return (MutableClock) clock;
    }

    /**
     * Registers a mutable, UTC {@link Clock} as the primary clock so tests can move
     * time forward to exercise expiry and cool-down windows.
     */
    @TestConfiguration
    static class MutableClockConfig {

        @Bean
        @Primary
        Clock testClock() {
            return new MutableClock();
        }
    }

    /**
     * A UTC clock whose instant can be advanced from tests.
     */
    static class MutableClock extends Clock {

        private Instant instant = Instant.parse("2026-01-01T00:00:00Z");

        void advanceMinutes(long minutes) {
            instant = instant.plusSeconds(minutes * 60);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
