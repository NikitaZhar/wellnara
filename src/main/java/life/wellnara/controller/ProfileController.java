package life.wellnara.controller;

import jakarta.servlet.http.HttpSession;
import life.wellnara.model.User;
import life.wellnara.service.AuthService;
import life.wellnara.service.SessionUserService;
import life.wellnara.service.UserProfileService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for editing personal data (profile) of provider users.
 *
 * <p>The provider profile is edited inline in the provider page (see
 * {@code /provider}, profile section); this controller only handles the
 * form submission and redirects back to that page.
 */
@Controller
public class ProfileController {

    private static final String LOGIN_REDIRECT = "redirect:/auth/login";
    private static final String PROVIDER_VIEW = "provider";

    private final UserProfileService userProfileService;
    private final AuthService authService;
    private final SessionUserService sessionUserService;
    private final ProviderPageModelAssembler providerPageModelAssembler;

    /**
     * Creates profile controller.
     *
     * @param userProfileService        service for user personal data
     * @param authService                service for password verification and change
     * @param sessionUserService         service for authenticated session user access
     * @param providerPageModelAssembler assembler for provider page model, used to
     *                                   re-render the provider page when the update fails
     */
    public ProfileController(UserProfileService userProfileService,
                             AuthService authService,
                             SessionUserService sessionUserService,
                             ProviderPageModelAssembler providerPageModelAssembler) {
        this.userProfileService = userProfileService;
        this.authService = authService;
        this.sessionUserService = sessionUserService;
        this.providerPageModelAssembler = providerPageModelAssembler;
    }

    /**
     * Updates the provider profile and, optionally, the provider password.
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
     * @param session            current HTTP session
     * @param model              MVC model
     * @return redirect to the provider profile section, or the provider page with an error
     */
    @PostMapping("/provider/profile")
    public String updateProviderProfile(@RequestParam String firstName,
                                        @RequestParam String lastName,
                                        @RequestParam(required = false) String phone,
                                        @RequestParam(required = false) String currentPassword,
                                        @RequestParam(required = false) String newPassword,
                                        @RequestParam(required = false) String confirmNewPassword,
                                        HttpSession session,
                                        Model model) {
        User currentUser = sessionUserService.requireProvider(session);
        if (currentUser == null) {
            return LOGIN_REDIRECT;
        }

        try {
            boolean passwordChangeRequested =
                    hasText(currentPassword) || hasText(newPassword) || hasText(confirmNewPassword);

            if (passwordChangeRequested) {
                if (!authService.verifyPassword(currentUser, currentPassword)) {
                    throw new IllegalArgumentException("Current password is incorrect");
                }
                if (!newPassword.equals(confirmNewPassword)) {
                    throw new IllegalArgumentException("New passwords do not match");
                }
            }

            userProfileService.updateProfile(currentUser, firstName, lastName, phone);

            if (passwordChangeRequested) {
                authService.changePassword(currentUser, newPassword);
            }

            return "redirect:/provider?section=profile&profileUpdated";
        } catch (IllegalArgumentException exception) {
            providerPageModelAssembler.populate(model, currentUser);
            model.addAttribute("profileFirstName", firstName);
            model.addAttribute("profileLastName", lastName);
            model.addAttribute("profilePhone", phone);
            model.addAttribute("profileError", exception.getMessage());
            return PROVIDER_VIEW;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
