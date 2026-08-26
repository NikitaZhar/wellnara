package life.wellnara.service;

import life.wellnara.exception.LocalizedException;
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
            throw new LocalizedException("error.password.required", "Password is required");
        }
        if (password.length() < MIN_LENGTH) {
            throw new LocalizedException("error.password.tooShort",
                    "Password must be at least " + MIN_LENGTH + " characters", MIN_LENGTH);
        }
    }
}
