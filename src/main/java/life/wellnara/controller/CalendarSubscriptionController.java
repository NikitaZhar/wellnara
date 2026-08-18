package life.wellnara.controller;

import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.service.calendar.CalendarSubscriptionService;
import life.wellnara.web.CurrentUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Handles the calendar feed subscription actions shared by both roles.
 *
 * <p>The routes live outside the role areas and are authenticated for any user;
 * each action operates on the current user and redirects back to that user's own
 * profile page. Enabling and regenerating are the same operation — both issue a
 * fresh, enabled token — so a disabled feed is turned back on with a new URL.
 */
@Controller
public class CalendarSubscriptionController {

    private final CalendarSubscriptionService subscriptionService;

    /**
     * Creates the calendar subscription controller.
     *
     * @param subscriptionService service managing the subscription lifecycle
     */
    public CalendarSubscriptionController(CalendarSubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /**
     * Enables the feed or rotates its token, then returns to the profile.
     *
     * @param currentUser authenticated user
     * @return redirect to the user's profile page
     */
    @PostMapping("/profile/calendar/regenerate")
    public String regenerate(@CurrentUser User currentUser) {
        subscriptionService.regenerate(currentUser);

        return profileRedirect(currentUser);
    }

    /**
     * Disables the feed, then returns to the profile.
     *
     * @param currentUser authenticated user
     * @return redirect to the user's profile page
     */
    @PostMapping("/profile/calendar/disable")
    public String disable(@CurrentUser User currentUser) {
        subscriptionService.disable(currentUser);

        return profileRedirect(currentUser);
    }

    private String profileRedirect(User user) {
        return user.getRole() == UserRole.PROVIDER
                ? "redirect:/provider/profile"
                : "redirect:/client/profile";
    }
}
