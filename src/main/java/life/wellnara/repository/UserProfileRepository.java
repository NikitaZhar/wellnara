package life.wellnara.repository;

import life.wellnara.model.User;
import life.wellnara.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository for user profiles.
 */
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    /**
     * Finds a profile by its owner.
     *
     * @param user profile owner
     * @return found profile or empty result
     */
    Optional<UserProfile> findByUser(User user);

    /**
     * Finds all profiles whose owner is in the given set, in a single query.
     *
     * <p>Used to resolve display names and phones for a page of users without N+1.
     *
     * @param users profile owners
     * @return profiles of the given owners
     */
    List<UserProfile> findAllByUserIn(Collection<User> users);

    /**
     * Deletes the profile belonging to the given user.
     *
     * @param user profile owner
     */
    void deleteByUser(User user);
}
