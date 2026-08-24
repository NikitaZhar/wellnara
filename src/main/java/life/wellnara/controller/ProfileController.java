package life.wellnara.controller;

import life.wellnara.model.User;
import life.wellnara.service.UserProfileService;
import life.wellnara.web.CurrentUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for editing personal data (profile) of provider users.
 *
 * <p>The provider profile is edited on its own page ({@code /provider/profile});
 * this controller only handles the form submission and redirects back to that
 * page on success, or re-renders it with the error and the submitted values on
 * failure.
 */
@Controller
public class ProfileController {

    private static final String PROFILE_VIEW = "provider-profile";

    private final UserProfileService userProfileService;
    private final ProviderPageModelAssembler providerPageModelAssembler;
    private final CalendarFeedModelAssembler calendarFeedModelAssembler;

    /**
     * Creates profile controller.
     *
     * @param userProfileService         service for user personal data
     * @param providerPageModelAssembler assembler for the provider profile model, used
     *                                   to re-render the profile page when the update fails
     * @param calendarFeedModelAssembler assembler for the calendar feed section, used
     *                                   to re-render the profile page when the update fails
     */
    public ProfileController(UserProfileService userProfileService,
                             ProviderPageModelAssembler providerPageModelAssembler,
                             CalendarFeedModelAssembler calendarFeedModelAssembler) {
        this.userProfileService = userProfileService;
        this.providerPageModelAssembler = providerPageModelAssembler;
        this.calendarFeedModelAssembler = calendarFeedModelAssembler;
    }

    /**
     * Updates the provider profile and, optionally, the password.
     *
     * <p>When any of the password fields is filled in, the current password is
     * verified and the new password confirmed <em>before</em> anything is saved,
     * so a wrong current password never leaves the name/phone update applied
     * without the password change (or vice versa).
     *
     * @param firstName          new first name
     * @param lastName           new last name
     * @param phone              new phone number, optional
     * @param currentPassword    current password, required only when changing the password
     * @param newPassword        new password, required only when changing the password
     * @param confirmNewPassword repeated new password, required only when changing the password
     * @param currentUser        authenticated provider
     * @param model              MVC model
     * @return redirect to the profile page, or the profile page re-rendered with an error
     */
    @PostMapping("/provider/profile")
    public String updateProviderProfile(@RequestParam String firstName,
                                        @RequestParam String lastName,
                                        @RequestParam(required = false) String phone,
                                        @RequestParam(required = false) String currentPassword,
                                        @RequestParam(required = false) String newPassword,
                                        @RequestParam(required = false) String confirmNewPassword,
                                        @CurrentUser User currentUser,
                                        Model model) {
        try {
            userProfileService.updateProfileAndPassword(currentUser,
                    firstName, lastName, phone,
                    currentPassword, newPassword, confirmNewPassword);

            return "redirect:/provider/profile?profileUpdated";
        } catch (IllegalArgumentException exception) {
            providerPageModelAssembler.populateProfile(model, currentUser);
            calendarFeedModelAssembler.populateFeed(model, currentUser);
            model.addAttribute("profileFirstName", firstName);
            model.addAttribute("profileLastName", lastName);
            model.addAttribute("profilePhone", phone);
            model.addAttribute("profileError", exception.getMessage());
            return PROFILE_VIEW;
        }
    }
}
