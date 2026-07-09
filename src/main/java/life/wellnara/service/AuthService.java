package life.wellnara.service;

import life.wellnara.model.User;
import life.wellnara.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service for authentication operations.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;

    /**
     * Creates authentication service.
     *
     * @param userRepository repository for user access
     */
    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Authenticates a user by username and password.
     *
     * @param username entered username
     * @param password entered password
     * @return authenticated user or empty result
     */
    public Optional<User> authenticate(String username, String password) {
        return userRepository.findByUsername(username)
                .filter(user -> user.getPassword().equals(password));
    }

    /**
     * Checks whether the given password matches the user's current password.
     *
     * <p>Read-only check, intended to be called before applying any other change
     * so that a wrong "current password" never leaves a partial update behind.
     *
     * @param user     user whose password is checked
     * @param password password entered for verification
     * @return true if the password matches
     */
    public boolean verifyPassword(User user, String password) {
        return password != null && user.getPassword().equals(password);
    }

    /**
     * Sets a new password for the given user.
     *
     * <p>Does not verify the current password itself; callers must verify it
     * via {@link #verifyPassword(User, String)} beforehand.
     *
     * @param user        user whose password is changed
     * @param newPassword new password value, must not be blank
     * @throws IllegalArgumentException if the new password is blank
     */
    @Transactional
    public void changePassword(User user, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("New password is required");
        }

        user.setPassword(newPassword);
        userRepository.save(user);
    }
}