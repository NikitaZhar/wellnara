package life.wellnara;

import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.UserRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link UserRepository}.
 * Verifies correct interaction with the database.
 */
@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Should save and retrieve user by username")
    void shouldSaveAndFindUserByUsername() {
        // Given
        User user = new User();
        user.setUsername("testuser");
        user.setPassword("password");
        user.setEmail("test@example.com");
        user.setRole(UserRole.SPECIALIST);

        // When
        userRepository.save(user);

        // Then
        assertThat(userRepository.findByUsername("testuser"))
                .isPresent()
                .get()
                .extracting(User::getEmail)
                .isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("Should return true when user exists by email")
    void shouldCheckUserExistsByEmail() {
        // Given
        User user = new User();
        user.setUsername("anotheruser");
        user.setPassword("password");
        user.setEmail("another@example.com");
        user.setRole(UserRole.CLIENT);

        userRepository.save(user);

        // When
        boolean exists = userRepository.existsByEmail("another@example.com");

        // Then
        assertThat(exists).isTrue();
    }
}