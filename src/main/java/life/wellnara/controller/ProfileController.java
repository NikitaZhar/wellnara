package life.wellnara.controller;

import life.wellnara.exception.LocalizedException;
import life.wellnara.model.User;
import life.wellnara.service.UserProfileService;
import life.wellnara.web.CurrentUser;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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
    private final MessageSource messageSource;

    /**
     * Creates profile controller.
     *
     * @param userProfileService         service for user personal data
     * @param providerPageModelAssembler assembler for the provider profile model, used
     *                                   to re-render the profile page when the update fails
     * @param messageSource              resolver of localized user-facing messages
     */
    public ProfileController(UserProfileService userProfileService,
                             ProviderPageModelAssembler providerPageModelAssembler,
                             MessageSource messageSource) {
        this.userProfileService = userProfileService;
        this.providerPageModelAssembler = providerPageModelAssembler;
        this.messageSource = messageSource;
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
     * @param whatsappUrl        WhatsApp contact link, optional (blank removes it)
     * @param telegramUrl        Telegram contact link, optional (blank removes it)
     * @param preferredCalendar  chosen calendar for the add-to-calendar action, optional (blank clears it)
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
                                        @RequestParam(required = false) String whatsappUrl,
                                        @RequestParam(required = false) String telegramUrl,
                                        @RequestParam(required = false) String preferredCalendar,
                                        @RequestParam(required = false) String currentPassword,
                                        @RequestParam(required = false) String newPassword,
                                        @RequestParam(required = false) String confirmNewPassword,
                                        @CurrentUser User currentUser,
                                        Model model) {
        try {
            userProfileService.updateProviderProfile(currentUser,
                    firstName, lastName, phone, whatsappUrl, telegramUrl, preferredCalendar,
                    currentPassword, newPassword, confirmNewPassword);

            return "redirect:/provider/profile?profileUpdated";
        } catch (IllegalArgumentException exception) {
            providerPageModelAssembler.populateProfile(model, currentUser);
            model.addAttribute("profileFirstName", firstName);
            model.addAttribute("profileLastName", lastName);
            model.addAttribute("profilePhone", phone);
            model.addAttribute("profileWhatsapp", whatsappUrl);
            model.addAttribute("profileTelegram", telegramUrl);
            model.addAttribute("preferredCalendar", preferredCalendar);
            model.addAttribute("profileError",
                    LocalizedException.resolve(exception, messageSource, LocaleContextHolder.getLocale()));
            return PROFILE_VIEW;
        }
    }
}
