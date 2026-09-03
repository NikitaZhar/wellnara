package life.wellnara;

import life.wellnara.exception.LocalizedException;
import life.wellnara.model.CalendarProvider;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.UserRepository;
import life.wellnara.service.UserProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for persisting the user's preferred calendar through the
 * profile update flow.
 */
@SpringBootTest
@Transactional
class UserProfilePreferredCalendarTest {

    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("A chosen calendar is saved on the user")
    void savesChosenCalendar() {
        User user = createClient("calendar-save");

        userProfileService.updateProfileAndPassword(
                user, "First", "Last", null, "GOOGLE", null, null, null);

        assertThat(reload(user).getPreferredCalendar()).isEqualTo(CalendarProvider.GOOGLE);
    }

    @Test
    @DisplayName("A blank calendar value clears the choice")
    void blankClearsChoice() {
        User user = createClient("calendar-clear");
        userProfileService.updateProfileAndPassword(
                user, "First", "Last", null, "OUTLOOK", null, null, null);
        assertThat(reload(user).getPreferredCalendar()).isEqualTo(CalendarProvider.OUTLOOK);

        userProfileService.updateProfileAndPassword(
                reload(user), "First", "Last", null, "  ", null, null, null);

        assertThat(reload(user).getPreferredCalendar()).isNull();
    }

    @Test
    @DisplayName("An unknown calendar value is rejected")
    void rejectsUnknownCalendar() {
        User user = createClient("calendar-bad");

        assertThatThrownBy(() -> userProfileService.updateProfileAndPassword(
                user, "First", "Last", null, "NOPE", null, null, null))
                .isInstanceOf(LocalizedException.class);
    }

    private User reload(User user) {
        return userRepository.findById(user.getId()).orElseThrow();
    }

    private User createClient(String usernamePrefix) {
        String username = usernamePrefix + "-" + System.nanoTime();

        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@test.com");
        user.setPassword("123");
        user.setRole(UserRole.CLIENT);

        return userRepository.save(user);
    }
}
