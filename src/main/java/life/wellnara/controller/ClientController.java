package life.wellnara.controller;

import jakarta.servlet.http.HttpSession;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for client page access.
 */
@Controller
public class ClientController {

    /**
     * Shows client page for authenticated client user.
     *
     * @param session current HTTP session
     * @return client page view name or redirect to login page
     */
    @GetMapping("/client")
    public String showPage(HttpSession session) {
        Object sessionUser = session.getAttribute("currentUser");

        if (!(sessionUser instanceof User currentUser)) {
            return "redirect:/auth/login";
        }

        if (currentUser.getRole() != UserRole.CLIENT) {
            return "redirect:/auth/login";
        }

        return "client";
    }
}