package life.wellnara.controller;

import jakarta.servlet.http.HttpSession;
import life.wellnara.model.User;
import life.wellnara.model.UserProfile;
import life.wellnara.service.SessionUserService;
import life.wellnara.service.UserProfileService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for editing personal data (profile) of provider and client users.
 */
@Controller
public class ProfileController {

    private static final String LOGIN_REDIRECT = "redirect:/auth/login";

    private final UserProfileService userProfileService;
    private final SessionUserService sessionUserService;

    /**
     * Creates profile controller.
     *
     * @param userProfileService service for user personal data
     * @param sessionUserService service for authenticated session user access
     */
    public ProfileController(UserProfileService userProfileService,
                             SessionUserService sessionUserService) {
        this.userProfileService = userProfileService;
        this.sessionUserService = sessionUserService;
    }

    /**
     * Shows the provider profile edit page.
     *
     * @param session current HTTP session
     * @param model   MVC model
     * @return provider profile page or redirect to login page
     */
    @GetMapping("/provider/profile")
    public String showProviderProfile(HttpSession session, Model model) {
        User currentUser = sessionUserService.requireProvider(session);
        if (currentUser == null) {
            return LOGIN_REDIRECT;
        }

        populateProfileModel(model, currentUser);
        return "provider-profile";
    }

    /**
     * Updates the provider profile.
     *
     * @param firstName new first name
     * @param lastName  new last name
     * @param phone     new phone number, optional
     * @param session   current HTTP session
     * @param model     MVC model
     * @return redirect to provider profile page or page with error
     */
    @PostMapping("/provider/profile")
    public String updateProviderProfile(@RequestParam String firstName,
                                        @RequestParam String lastName,
                                        @RequestParam(required = false) String phone,
                                        HttpSession session,
                                        Model model) {
        User currentUser = sessionUserService.requireProvider(session);
        if (currentUser == null) {
            return LOGIN_REDIRECT;
        }

        try {
            userProfileService.updateProfile(currentUser, firstName, lastName, phone);
            return "redirect:/provider/profile?updated";
        } catch (IllegalArgumentException exception) {
            model.addAttribute("username", currentUser.getUsername());
            model.addAttribute("email", currentUser.getEmail());
            model.addAttribute("firstName", firstName);
            model.addAttribute("lastName", lastName);
            model.addAttribute("phone", phone);
            model.addAttribute("profileError", exception.getMessage());
            return "provider-profile";
        }
    }

    /**
     * Shows the client profile edit page.
     *
     * @param session current HTTP session
     * @param model   MVC model
     * @return client profile page or redirect to login page
     */
//    @GetMapping("/client/profile")
//    public String showClientProfile(HttpSession session, Model model) {
//        User currentUser = sessionUserService.requireClient(session);
//        if (currentUser == null) {
//            return LOGIN_REDIRECT;
//        }
//
//        populateProfileModel(model, currentUser);
//        return "client-profile";
//    }

    /**
     * Updates the client profile.
     *
     * @param firstName new first name
     * @param lastName  new last name
     * @param phone     new phone number, optional
     * @param session   current HTTP session
     * @param model     MVC model
     * @return redirect to client profile page or page with error
     */
//    @PostMapping("/client/profile")
//    public String updateClientProfile(@RequestParam String firstName,
//                                      @RequestParam String lastName,
//                                      @RequestParam(required = false) String phone,
//                                      HttpSession session,
//                                      Model model) {
//        User currentUser = sessionUserService.requireClient(session);
//        if (currentUser == null) {
//            return LOGIN_REDIRECT;
//        }
//
//        try {
//            userProfileService.updateProfile(currentUser, firstName, lastName, phone);
//            return "redirect:/client/profile?updated";
//        } catch (IllegalArgumentException exception) {
//            model.addAttribute("username", currentUser.getUsername());
//            model.addAttribute("email", currentUser.getEmail());
//            model.addAttribute("firstName", firstName);
//            model.addAttribute("lastName", lastName);
//            model.addAttribute("phone", phone);
//            model.addAttribute("profileError", exception.getMessage());
//            return "client-profile";
//        }
//    }

    private void populateProfileModel(Model model, User user) {
        UserProfile profile = userProfileService.getProfile(user);

        model.addAttribute("username", user.getUsername());
        model.addAttribute("email", user.getEmail());
        model.addAttribute("firstName", profile.getFirstName());
        model.addAttribute("lastName", profile.getLastName());
        model.addAttribute("phone", profile.getPhone());
    }
}
