package life.wellnara.controller;

import jakarta.servlet.http.HttpSession;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.service.ClientInvitationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for client invitation from provider page.
 */
@Controller
public class ClientInvitationController {

    private final ClientInvitationService clientInvitationService;

    /**
     * Creates client invitation controller.
     *
     * @param clientInvitationService service for client invitations
     */
    public ClientInvitationController(ClientInvitationService clientInvitationService) {
        this.clientInvitationService = clientInvitationService;
    }

    /**
     * Creates client invitation and stores registration link in session.
     *
     * @param email invited client email
     * @param session current HTTP session
     * @param model MVC model
     * @return redirect to provider page or provider page with validation error
     */
    @PostMapping("/provider/invite-client")
    public String inviteClient(@RequestParam String email,
                               HttpSession session) {
        User currentUser = getAuthenticatedProvider(session);

        if (currentUser == null) {
            return "redirect:/auth/login";
        }

        try {
            String token = clientInvitationService.invite(currentUser, email);
            String link = "http://localhost:8080/client/register?token=" + token;

            session.setAttribute("clientInviteLink", link);
            session.removeAttribute("clientInviteError");

        } catch (IllegalArgumentException exception) {
            session.setAttribute("clientInviteError", exception.getMessage());
            session.removeAttribute("clientInviteLink");
        }

        return "redirect:/provider";
    }    
    /**
     * Returns authenticated provider from current session.
     *
     * @param session current HTTP session
     * @return authenticated provider or null
     */
    private User getAuthenticatedProvider(HttpSession session) {
        Object sessionUser = session.getAttribute("currentUser");

        if (!(sessionUser instanceof User currentUser)) {
            return null;
        }

        if (currentUser.getRole() != UserRole.PROVIDER) {
            return null;
        }

        return currentUser;
    }
}