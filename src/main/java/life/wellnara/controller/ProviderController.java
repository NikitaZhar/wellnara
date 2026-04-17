package life.wellnara.controller;

import jakarta.servlet.http.HttpSession;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for provider page access.
 */
@Controller
public class ProviderController {

    /**
     * Shows provider page for authenticated provider user.
     *
     * @param session current HTTP session
     * @return provider page view name or redirect to login page
     */
    @GetMapping("/provider")
    public String showPage(HttpSession session) {
        Object sessionUser = session.getAttribute("currentUser");

        if (!(sessionUser instanceof User currentUser)) {
            return "redirect:/auth/login";
        }

        if (currentUser.getRole() != UserRole.PROVIDER) {
            return "redirect:/auth/login";
        }

        return "provider";
    }
}