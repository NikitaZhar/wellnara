package life.wellnara.controller;

import jakarta.servlet.http.HttpSession;
import life.wellnara.model.Offering;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.service.OfferingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/**
 * Controller for offering management.
 */
@Controller
public class OfferingController {

    private final OfferingService offeringService;

    /**
     * Creates offering controller.
     *
     * @param offeringService service for offering management
     */
    public OfferingController(OfferingService offeringService) {
        this.offeringService = offeringService;
    }

    /**
     * Creates offering.
     *
     * @param name offering name
     * @param description offering description
     * @param pricePerSession price per session
     * @param durationMinutes session duration in minutes
     * @param session current HTTP session
     * @return redirect to provider page or login page
     */
    @PostMapping("/provider/offerings")
    public String createOffering(@RequestParam String name,
                                 @RequestParam String description,
                                 @RequestParam BigDecimal pricePerSession,
                                 @RequestParam Integer durationMinutes,
                                 HttpSession session) {
        User currentUser = getCurrentProvider(session);
        if (currentUser == null) {
            return "redirect:/auth/login";
        }

        offeringService.createOffering(
                currentUser,
                name,
                description,
                pricePerSession,
                durationMinutes
        );

        return "redirect:/provider?section=offerings";
    }

    /**
     * Shows offering edit page.
     *
     * @param offeringId offering identifier
     * @param session current HTTP session
     * @param model MVC model
     * @return edit page or redirect to login page
     */
    @GetMapping("/provider/offerings/{offeringId}/edit")
    public String showEditPage(@PathVariable Long offeringId,
                               HttpSession session,
                               Model model) {
        User currentUser = getCurrentProvider(session);
        if (currentUser == null) {
            return "redirect:/auth/login";
        }

        Offering offering = offeringService.getOfferingOfProvider(currentUser, offeringId);
        model.addAttribute("offering", offering);

        return "offering-edit";
    }

    /**
     * Updates offering.
     *
     * @param offeringId offering identifier
     * @param name offering name
     * @param description offering description
     * @param pricePerSession price per session
     * @param durationMinutes session duration in minutes
     * @param session current HTTP session
     * @return redirect to provider page or login page
     */
    @PostMapping("/provider/offerings/{offeringId}/edit")
    public String updateOffering(@PathVariable Long offeringId,
                                 @RequestParam String name,
                                 @RequestParam String description,
                                 @RequestParam BigDecimal pricePerSession,
                                 @RequestParam Integer durationMinutes,
                                 HttpSession session) {
        User currentUser = getCurrentProvider(session);
        if (currentUser == null) {
            return "redirect:/auth/login";
        }

        offeringService.updateOffering(
                currentUser,
                offeringId,
                name,
                description,
                pricePerSession,
                durationMinutes
        );

        return "redirect:/provider?section=offerings";
    }

    /**
     * Returns authenticated provider from session.
     *
     * @param session current HTTP session
     * @return provider user or null
     */
    private User getCurrentProvider(HttpSession session) {
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