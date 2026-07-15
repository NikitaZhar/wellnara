package life.wellnara.controller;

import jakarta.servlet.http.HttpSession;
import life.wellnara.service.AdminUserService;
import life.wellnara.service.ProviderInvitationService;
import life.wellnara.service.email.InvitationNotificationService;
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

    /**
     * Creates the provider invitation controller.
     *
     * @param providerInvitationService     provider invitation flow
     * @param adminUserService              admin user operations
     * @param invitationNotificationService sends invitation emails
     */
    public ProviderInvitationController(ProviderInvitationService providerInvitationService,
                                        AdminUserService adminUserService,
                                        InvitationNotificationService invitationNotificationService) {
        this.providerInvitationService = providerInvitationService;
        this.adminUserService = adminUserService;
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
