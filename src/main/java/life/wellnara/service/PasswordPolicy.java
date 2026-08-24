package life.wellnara.service;

import org.springframework.stereotype.Component;

/**
 * The single password rule applied wherever a user chooses a password
 * (registration, password change and password reset): non-blank and at least
 * {@link #MIN_LENGTH} characters.
 */
@Component
public class PasswordPolicy {

    /** Minimum number of characters a user password must have. */
    public static final int MIN_LENGTH = 8;

    /**
     * Validates a user-chosen password against the policy.
     *
     * @param password candidate password
     * @throws IllegalArgumentException if the password is blank or shorter than {@link #MIN_LENGTH}
     */
    public void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("Password must be at least " + MIN_LENGTH + " characters");
        }
    }
}
