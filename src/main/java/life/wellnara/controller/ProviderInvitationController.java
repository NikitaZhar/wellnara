package life.wellnara.controller;

import jakarta.servlet.http.HttpSession;
import life.wellnara.model.User;
import life.wellnara.service.AdminUserService;
import life.wellnara.service.ProviderInvitationService;
import life.wellnara.service.SessionUserService;
import life.wellnara.service.email.InvitationNotificationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for provider invitation from the admin page.
 */
@Controller
public class ProviderInvitationController {

    private final ProviderInvitationService providerInvitationService;
    private final AdminUserService adminUserService;
    private final SessionUserService sessionUserService;
    private final InvitationNotificationService invitationNotificationService;

    /**
     * Creates the provider invitation controller.
     *
     * @param providerInvitationService     provider invitation flow
     * @param adminUserService              admin user operations
     * @param sessionUserService            authenticated session user access
     * @param invitationNotificationService sends invitation emails
     */
    public ProviderInvitationController(ProviderInvitationService providerInvitationService,
                                        AdminUserService adminUserService,
                                        SessionUserService sessionUserService,
                                        InvitationNotificationService invitationNotificationService) {
        this.providerInvitationService = providerInvitationService;
        this.adminUserService = adminUserService;
        this.sessionUserService = sessionUserService;
        this.invitationNotificationService = invitationNotificationService;
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
        User currentUser = sessionUserService.requireAdmin(session);
        if (currentUser == null) {
            return "redirect:/auth/login";
        }

        try {
            String token = providerInvitationService.invite(email);
            invitationNotificationService.sendProviderInvitation(email, token);

            session.setAttribute("providerInviteSuccessMessage", "Provider invitation was sent to " + email);
            return "redirect:/admin";
        } catch (IllegalArgumentException exception) {
            session.removeAttribute("providerInviteSuccessMessage");
            model.addAttribute("inviteError", exception.getMessage());
            model.addAttribute("users", adminUserService.getAllUsersExceptAdmins());
            return "admin";
        }
    }
}
