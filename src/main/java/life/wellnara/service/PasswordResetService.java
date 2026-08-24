package life.wellnara.service;

import life.wellnara.model.PasswordResetToken;
import life.wellnara.model.User;
import life.wellnara.repository.PasswordResetTokenRepository;
import life.wellnara.repository.UserRepository;
import life.wellnara.service.email.PasswordResetNotificationService;
import life.wellnara.service.time.ApplicationTimeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Drives the "forgot password" flow: issuing single-use reset links and
 * consuming them to set a new password.
 *
 * <p>Only token hashes are stored (the raw token lives in the emailed link). A
 * link is valid for {@code ttlMinutes} and only once. To avoid leaking which
 * emails have accounts, {@link #requestReset(String)} behaves identically for
 * known and unknown addresses. A short per-account cool-down prevents a burst of
 * reset emails to the same account.
 */
@Service
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final AuthService authService;
    private final PasswordResetNotificationService notificationService;
    private final ApplicationTimeService applicationTimeService;
    private final long ttlMinutes;
    private final long cooldownMinutes;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder tokenEncoder = Base64.getUrlEncoder().withoutPadding();

    /**
     * Creates the password reset service.
     *
     * @param userRepository        repository for user lookup by email
     * @param tokenRepository       repository for reset tokens
     * @param authService           service that applies the new password (with the policy)
     * @param notificationService   service that emails the reset link
     * @param applicationTimeService source of current application time (UTC)
     * @param ttlMinutes            how long a reset link stays valid, in minutes
     * @param cooldownMinutes       minimum minutes between reset emails to one account
     */
    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                AuthService authService,
                                PasswordResetNotificationService notificationService,
                                ApplicationTimeService applicationTimeService,
                                @Value("${wellnara.password-reset.ttl-minutes:60}") long ttlMinutes,
                                @Value("${wellnara.password-reset.cooldown-minutes:2}") long cooldownMinutes) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.authService = authService;
        this.notificationService = notificationService;
        this.applicationTimeService = applicationTimeService;
        this.ttlMinutes = ttlMinutes;
        this.cooldownMinutes = cooldownMinutes;
    }

    /**
     * Requests a password reset for the given email. Always succeeds silently: if
     * no account has that email nothing is sent; if a still-valid link was issued
     * within the cool-down window no new email is sent; otherwise earlier unused
     * links are invalidated and a fresh link is emailed.
     *
     * @param email the email entered on the "forgot password" form
     */
    @Transactional
    public void requestReset(String email) {
        Optional<User> maybeUser = userRepository.findByEmail(email);
        if (maybeUser.isEmpty()) {
            return; // unknown email: behave the same, send nothing (no account enumeration)
        }
        User user = maybeUser.get();
        LocalDateTime now = applicationTimeService.currentUtcDateTime();

        List<PasswordResetToken> unusedTokens =
                tokenRepository.findAllByUser_IdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId());

        if (!unusedTokens.isEmpty()) {
            PasswordResetToken latest = unusedTokens.get(0);
            if (latest.isUsable(now) && latest.getCreatedAt().isAfter(now.minusMinutes(cooldownMinutes))) {
                return; // a fresh link was just sent — do not resend within the cool-down
            }
        }

        // Only the newest link may work: invalidate any earlier unused ones.
        unusedTokens.forEach(token -> token.markUsed(now));

        String rawToken = generateRawToken();
        tokenRepository.save(new PasswordResetToken(
                user, hash(rawToken), now, now.plusMinutes(ttlMinutes)));

        notificationService.sendResetLink(user.getEmail(), rawToken);
    }

    /**
     * Whether a reset token is currently usable (exists, not used, not expired).
     * Used to decide whether to show the "choose a new password" form.
     *
     * @param rawToken raw token from the reset link
     * @return true if the token can still be used
     */
    @Transactional(readOnly = true)
    public boolean isTokenUsable(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }
        LocalDateTime now = applicationTimeService.currentUtcDateTime();
        return tokenRepository.findByTokenHash(hash(rawToken))
                .map(token -> token.isUsable(now))
                .orElse(false);
    }

    /**
     * Consumes a reset token and sets a new password, atomically.
     *
     * @param rawToken           raw token from the reset link
     * @param newPassword        new password (validated by {@link PasswordPolicy})
     * @param confirmNewPassword repeated new password
     * @throws IllegalStateException    if the token is missing, already used or expired
     * @throws IllegalArgumentException if the passwords differ or violate the policy
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword, String confirmNewPassword) {
        LocalDateTime now = applicationTimeService.currentUtcDateTime();

        PasswordResetToken token = Optional.ofNullable(rawToken)
                .filter(raw -> !raw.isBlank())
                .flatMap(raw -> tokenRepository.findByTokenHash(hash(raw)))
                .filter(existing -> existing.isUsable(now))
                .orElseThrow(() -> new IllegalStateException("This reset link is invalid or has expired"));

        if (!Objects.equals(newPassword, confirmNewPassword)) {
            throw new IllegalArgumentException("New passwords do not match");
        }

        authService.changePassword(token.getUser(), newPassword); // enforces PasswordPolicy
        token.markUsed(now);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return tokenEncoder.encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required to hash reset tokens", exception);
        }
    }
}
