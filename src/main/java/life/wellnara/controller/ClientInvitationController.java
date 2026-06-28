package life.wellnara.controller;

import jakarta.servlet.http.HttpSession;
import life.wellnara.model.User;
import life.wellnara.service.ClientInvitationService;
import life.wellnara.service.SessionUserService;
import life.wellnara.service.email.InvitationNotificationService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for client invitation from the provider page.
 */
@Controller
public class ClientInvitationController {

    private final ClientInvitationService clientInvitationService;
    private final SessionUserService sessionUserService;
    private final InvitationNotificationService invitationNotificationService;

    /**
     * Creates the client invitation controller.
     *
     * @param clientInvitationService       client invitation flow
     * @param sessionUserService            authenticated session user access
     * @param invitationNotificationService sends invitation emails
     */
    public ClientInvitationController(ClientInvitationService clientInvitationService,
                                      SessionUserService sessionUserService,
                                      InvitationNotificationService invitationNotificationService) {
        this.clientInvitationService = clientInvitationService;
        this.sessionUserService = sessionUserService;
        this.invitationNotificationService = invitationNotificationService;
    }

    /**
     * Creates a client invitation and sends the registration link by email.
     *
     * @param email   invited client email
     * @param session current HTTP session
     * @return redirect to the provider page
     */
    @PostMapping("/provider/invite-client")
    public String inviteClient(@RequestParam String email, HttpSession session) {
        User currentUser = sessionUserService.requireProvider(session);
        if (currentUser == null) {
            return "redirect:/auth/login";
        }

        try {
            String token = clientInvitationService.invite(currentUser, email);
            invitationNotificationService.sendClientInvitation(email, token);

            session.setAttribute("clientInviteSuccessMessage", "Приглашение отправлено на " + email);
            session.removeAttribute("clientInviteError");
        } catch (IllegalArgumentException exception) {
            session.setAttribute("clientInviteError", exception.getMessage());
            session.removeAttribute("clientInviteSuccessMessage");
        }

        return "redirect:/provider";
    }
}
