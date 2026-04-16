package life.wellnara.service;

import life.wellnara.model.User;
import life.wellnara.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service for working with users.
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    /**
     * Creates a service with user repository dependency.
     *
     * @param userRepository repository for user persistence operations
     */
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Saves a user.
     *
     * @param user user to save
     * @return saved user
     */
    public User save(User user) {
        return userRepository.save(user);
    }

    /**
     * Finds a user by id.
     *
     * @param id user identifier
     * @return found user or empty result
     */
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    /**
     * Finds a user by username.
     *
     * @param username username of the user
     * @return found user or empty result
     */
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * Finds a user by email.
     *
     * @param email email of the user
     * @return found user or empty result
     */
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Checks whether a user with the given username exists.
     *
     * @param username username to check
     * @return true if user exists, otherwise false
     */
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    /**
     * Checks whether a user with the given email exists.
     *
     * @param email email to check
     * @return true if user exists, otherwise false
     */
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }
}