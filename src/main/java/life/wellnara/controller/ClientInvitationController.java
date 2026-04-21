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
                               HttpSession session,
                               Model model) {
        Object sessionUser = session.getAttribute("currentUser");

        if (!(sessionUser instanceof User currentUser)) {
            return "redirect:/auth/login";
        }

        if (currentUser.getRole() != UserRole.PROVIDER) {
            return "redirect:/auth/login";
        }

        try {
            String token = clientInvitationService.invite(currentUser, email);
            String registrationLink = "http://localhost:8080/client/register?token=" + token;

            session.setAttribute("clientInviteLink", registrationLink);

            return "redirect:/provider";
        } catch (IllegalArgumentException exception) {
            session.removeAttribute("clientInviteLink");
            model.addAttribute("clientInviteError", exception.getMessage());
            return "provider";
        }
    }
}