package life.wellnara;

import life.wellnara.controller.CalendarSubscriptionController;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.service.calendar.CalendarSubscriptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for the subscription actions: each action operates on the
 * current user and redirects back to that user's own profile page by role.
 */
class CalendarSubscriptionControllerTest {

    private final CalendarSubscriptionService subscriptionService = mock(CalendarSubscriptionService.class);
    private final CalendarSubscriptionController controller =
            new CalendarSubscriptionController(subscriptionService);

    @Test
    @DisplayName("Regenerate rotates the token and returns to the provider profile")
    void regenerateForProvider() {
        User provider = user(UserRole.PROVIDER);

        String view = controller.regenerate(provider);

        verify(subscriptionService).regenerate(provider);
        assertThat(view).isEqualTo("redirect:/provider/profile");
    }

    @Test
    @DisplayName("Regenerate returns to the client profile for a client")
    void regenerateForClient() {
        User client = user(UserRole.CLIENT);

        assertThat(controller.regenerate(client)).isEqualTo("redirect:/client/profile");
    }

    @Test
    @DisplayName("Disable turns the feed off and returns to the provider profile")
    void disableForProvider() {
        User provider = user(UserRole.PROVIDER);

        String view = controller.disable(provider);

        verify(subscriptionService).disable(provider);
        assertThat(view).isEqualTo("redirect:/provider/profile");
    }

    private User user(UserRole role) {
        User user = mock(User.class);
        when(user.getRole()).thenReturn(role);
        return user;
    }
}
