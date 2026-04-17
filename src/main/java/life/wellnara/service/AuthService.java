package life.wellnara.service;

import life.wellnara.model.User;
import life.wellnara.repository.UserRepository;
import org.springframework.stereotype.Service;

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
}