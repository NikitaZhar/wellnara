package life.wellnara.service;

import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for admin operations related to system users.
 */
@Service
public class AdminUserService {

    private final UserRepository userRepository;

    /**
     * Creates admin user service.
     *
     * @param userRepository repository for user operations
     */
    public AdminUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Returns all system users except administrators.
     *
     * @return list of users except admins
     */
    @Transactional(readOnly = true)
    public List<User> getAllUsersExceptAdmins() {
        return userRepository.findAllByRoleNot(UserRole.ADMIN);
    }

    /**
     * Deletes user by id if this user is not an administrator.
     *
     * @param userId user identifier
     */
    @Transactional
    public void deleteNonAdminUser(Long userId) {
        User user = userRepository.findByIdAndRoleNot(userId, UserRole.ADMIN)
                .orElseThrow(() -> new IllegalArgumentException("User not found or cannot be deleted"));

        userRepository.delete(user);
    }
}