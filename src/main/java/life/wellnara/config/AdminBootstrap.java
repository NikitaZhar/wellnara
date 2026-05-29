package life.wellnara.config;

import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Initializes the single system administrator account.
 */
@Component
public class AdminBootstrap implements CommandLineRunner {

    private final UserRepository userRepository;
    private final String adminUsername;
    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrap(UserRepository userRepository,
                          @Value("${wellnara.admin.username}") String adminUsername,
                          @Value("${wellnara.admin.email}") String adminEmail,
                          @Value("${wellnara.admin.password}") String adminPassword) {
        this.userRepository = userRepository;
        this.adminUsername = adminUsername;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (userRepository.existsByRole(UserRole.ADMIN)) {
            return;
        }

        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setEmail(adminEmail);
        admin.setPassword(adminPassword);
        admin.setRole(UserRole.ADMIN);

        userRepository.save(admin);
    }
}