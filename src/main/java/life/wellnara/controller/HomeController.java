package life.wellnara.controller;

import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.web.CurrentUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for the post-login Home page.
 *
 * <p>Home is the landing page after login. It shows a read-only summary of the
 * current state (variant A: derived from existing data, no event store).
 * Provider and client have their own Home; admin keeps its existing landing
 * page until an admin Home is built.
 */
@Controller
public class HomeController {

    private final HomePageModelAssembler homePageModelAssembler;

    /**
     * Creates home controller.
     *
     * @param homePageModelAssembler assembler for the home page model
     */
    public HomeController(HomePageModelAssembler homePageModelAssembler) {
        this.homePageModelAssembler = homePageModelAssembler;
    }

    /**
     * Shows the role-specific Home page for the authenticated user.
     *
     * @param currentUser authenticated user
     * @param model       MVC model
     * @return home view name or redirect to the role landing page
     */
    @GetMapping("/home")
    public String showHome(@CurrentUser User currentUser, Model model) {
        UserRole role = currentUser.getRole();

        if (role == UserRole.PROVIDER) {
            homePageModelAssembler.populateProviderHome(model, currentUser);
            return "provider-home";
        }

        if (role == UserRole.CLIENT) {
            homePageModelAssembler.populateClientHome(model, currentUser);
            return "client-home";
        }

        // Admin Home comes later; keep the existing landing for now.
        if (role == UserRole.ADMIN) {
            return "redirect:/admin";
        }

        return "redirect:/auth/login";
    }
}
