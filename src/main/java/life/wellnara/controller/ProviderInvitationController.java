package life.wellnara.controller;

import jakarta.servlet.http.HttpSession;
import life.wellnara.exception.LocalizedException;
import life.wellnara.service.AdminUserService;
import life.wellnara.service.ProviderInvitationService;
import life.wellnara.service.email.InvitationNotificationService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for provider invitation from the admin page.
 *
 * <p>Access is restricted to the {@code ADMIN} role by the security filter
 * chain.
 */
@Controller
public class ProviderInvitationController {

    private final ProviderInvitationService providerInvitationService;
    private final AdminUserService adminUserService;
    private final InvitationNotificationService invitationNotificationService;
    private final MessageSource messageSource;

    /**
     * Creates the provider invitation controller.
     *
     * @param providerInvitationService     provider invitation flow
     * @param adminUserService              admin user operations
     * @param invitationNotificationService sends invitation emails
     * @param messageSource                 resolver of localized user-facing messages
     */
    public ProviderInvitationController(ProviderInvitationService providerInvitationService,
                                        AdminUserService adminUserService,
                                        InvitationNotificationService invitationNotificationService,
                                        MessageSource messageSource) {
        this.providerInvitationService = providerInvitationService;
        this.adminUserService = adminUserService;
        this.invitationNotificationService = invitationNotificationService;
        this.messageSource = messageSource;
    }

    private String msg(String code, Object... args) {
        return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
    }

    /**
     * Creates a provider invitation and sends the registration link by email.
     *
     * @param email   invited provider email
     * @param session current HTTP session
     * @param model   MVC model
     * @return redirect to the admin page, or the admin page with a validation error
     */
    @PostMapping("/admin/invite")
    public String invite(@RequestParam String email,
                         HttpSession session,
                         Model model) {
        try {
            String token = providerInvitationService.invite(email);
            invitationNotificationService.sendProviderInvitation(email, token);

            session.setAttribute("providerInviteSuccessMessage", msg("error.invite.provider.sent", email));
            return "redirect:/admin";
        } catch (IllegalArgumentException exception) {
            session.removeAttribute("providerInviteSuccessMessage");
            model.addAttribute("inviteError",
                    LocalizedException.resolve(exception, messageSource, LocaleContextHolder.getLocale()));
            model.addAttribute("users", adminUserService.getAllUsersExceptAdmins());
            return "admin";
        } catch (MailException exception) {
            session.removeAttribute("providerInviteSuccessMessage");
            model.addAttribute("inviteError", msg("error.invite.mailFailed", email));
            model.addAttribute("users", adminUserService.getAllUsersExceptAdmins());
            return "admin";
        }
    }
}
