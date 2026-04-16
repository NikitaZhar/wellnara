package life.wellnara.repository;

import life.wellnara.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * Repository for working with User entities.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by username.
     *
     * @param username username of the user
     * @return found user or empty result
     */
    Optional<User> findByUsername(String username);

    /**
     * Finds a user by email.
     *
     * @param email email of the user
     * @return found user or empty result
     */
    Optional<User> findByEmail(String email);

    /**
     * Checks whether a user with the given username exists.
     *
     * @param username username to check
     * @return true if user exists, otherwise false
     */
    boolean existsByUsername(String username);

    /**
     * Checks whether a user with the given email exists.
     *
     * @param email email to check
     * @return true if user exists, otherwise false
     */
    boolean existsByEmail(String email);
}