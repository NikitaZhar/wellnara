package life.wellnara.controller;

import jakarta.servlet.http.HttpSession;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.service.ProviderClientService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Controller for provider client management.
 */
@Controller
public class ProviderClientController {

    private final ProviderClientService providerClientService;

    /**
     * Creates provider client controller.
     *
     * @param providerClientService service for provider client operations
     */
    public ProviderClientController(ProviderClientService providerClientService) {
        this.providerClientService = providerClientService;
    }

    /**
     * Deletes client of current provider.
     *
     * @param clientId client identifier
     * @param session current HTTP session
     * @return redirect to provider page or login page
     */
    @PostMapping("/provider/clients/{clientId}/delete")
    public String deleteClient(@PathVariable Long clientId, HttpSession session) {
        Object sessionUser = session.getAttribute("currentUser");

        if (!(sessionUser instanceof User currentUser)) {
            return "redirect:/auth/login";
        }

        if (currentUser.getRole() != UserRole.PROVIDER) {
            return "redirect:/auth/login";
        }

        providerClientService.deleteClient(currentUser, clientId);

        return "redirect:/provider";
    }
}