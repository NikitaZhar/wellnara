package life.wellnara.controller;

import jakarta.servlet.http.HttpSession;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.service.ClientOfferingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for client page access.
 */
@Controller
public class ClientController {

    private final ClientOfferingService clientOfferingService;

    /**
     * Creates client controller.
     *
     * @param clientOfferingService service for client access to provider offerings
     */
    public ClientController(ClientOfferingService clientOfferingService) {
        this.clientOfferingService = clientOfferingService;
    }

    /**
     * Shows client page for authenticated client user.
     *
     * @param session current HTTP session
     * @param model MVC model
     * @return client page view name or redirect to login page
     */
    @GetMapping("/client")
    public String showPage(HttpSession session, Model model) {
        Object sessionUser = session.getAttribute("currentUser");

        if (!(sessionUser instanceof User currentUser)) {
            return "redirect:/auth/login";
        }

        if (currentUser.getRole() != UserRole.CLIENT) {
            return "redirect:/auth/login";
        }

        model.addAttribute("clientName", currentUser.getUsername());
        model.addAttribute("offerings", clientOfferingService.getOfferingsOfClientProvider(currentUser));

        return "client";
    }
}