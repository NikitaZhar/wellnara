package life.wellnara.controller;

import jakarta.servlet.http.HttpSession;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.service.OfferingService;
import life.wellnara.service.ProviderClientService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for provider page access.
 */
@Controller
public class ProviderController {

    private final ProviderClientService providerClientService;
    private final OfferingService offeringService;

    /**
     * Creates provider controller.
     *
     * @param providerClientService service for provider client operations
     * @param offeringService service for offering management
     */
    public ProviderController(ProviderClientService providerClientService,
                              OfferingService offeringService) {
        this.providerClientService = providerClientService;
        this.offeringService = offeringService;
    }

    /**
     * Shows provider page for authenticated provider user.
     *
     * @param session current HTTP session
     * @param model MVC model
     * @return provider page view name or redirect to login page
     */
    @GetMapping("/provider")
    public String showPage(HttpSession session, Model model) {
        Object sessionUser = session.getAttribute("currentUser");

        if (!(sessionUser instanceof User currentUser)) {
            return "redirect:/auth/login";
        }

        if (currentUser.getRole() != UserRole.PROVIDER) {
            return "redirect:/auth/login";
        }

        Object clientInviteLink = session.getAttribute("clientInviteLink");
        if (clientInviteLink != null) {
            model.addAttribute("clientInviteLink", clientInviteLink);
            session.removeAttribute("clientInviteLink");
        }

        model.addAttribute("clients", providerClientService.getClientsOfProvider(currentUser));
        model.addAttribute("offerings", offeringService.getOfferingsOfProvider(currentUser));
        model.addAttribute("providerName", currentUser.getUsername());

        return "provider";
    }
}