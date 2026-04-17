package life.wellnara;

import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * Integration tests for {@link UserRepository}.
 */
@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Should save and retrieve user by username")
    void shouldSaveAndFindUserByUsername() {
        User user = new User();
        user.setUsername("testuser");
        user.setPassword("password");
        user.setEmail("test@example.com");
        user.setRole(UserRole.PROVIDER);

        userRepository.save(user);

        assertThat(userRepository.findByUsername("testuser"))
                .isPresent()
                .get()
                .extracting(User::getEmail)
                .isEqualTo("test@example.com");
    }
}