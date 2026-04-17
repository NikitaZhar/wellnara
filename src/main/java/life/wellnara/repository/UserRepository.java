package life.wellnara.repository;

import life.wellnara.model.User;
import life.wellnara.model.UserRole;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for working with users.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by username.
     *
     * @param username username value
     * @return found user or empty result
     */
    Optional<User> findByUsername(String username);
    
    /**
     * Checks whether user with given email already exists.
     *
     * @param email email value
     * @return true if user exists
     */
    boolean existsByEmail(String email);
    
    /**
     * Returns all users whose role is not equal to provided role.
     *
     * @param role excluded role
     * @return list of users except specified role
     */
    List<User> findAllByRoleNot(UserRole role);

    /**
     * Finds user by id whose role is not equal to provided role.
     *
     * @param id user id
     * @param role excluded role
     * @return found user or empty result
     */
    Optional<User> findByIdAndRoleNot(Long id, UserRole role);
}