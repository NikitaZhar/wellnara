package life.wellnara.controller;

import jakarta.servlet.http.HttpSession;
import life.wellnara.model.User;
import life.wellnara.service.ProviderInvitationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for specialist registration by invitation token.
 */
@Controller
public class ProviderRegistrationController {

    private final ProviderInvitationService service;

    /**
     * Creates provider registration controller.
     *
     * @param service provider invitation service
     */
    public ProviderRegistrationController(ProviderInvitationService service) {
        this.service = service;
    }

    /**
     * Shows specialist registration page.
     *
     * @param token invitation token
     * @param model MVC model
     * @return registration page view name
     */
    @GetMapping("/provider/register")
    public String showRegisterPage(@RequestParam String token, Model model) {
        try {
            String email = service.getEmailByToken(token);
            model.addAttribute("token", token);
            model.addAttribute("email", email);
            return "provider-register";
        } catch (IllegalArgumentException exception) {
            model.addAttribute("error", exception.getMessage());
            return "login";
        }
    }

    /**
     * Registers provider by invitation token.
     *
     * @param token invitation token
     * @param name provider name
     * @param password provider password
     * @param confirmPassword repeated provider password
     * @param session current HTTP session
     * @param model MVC model
     * @return redirect to provider page on success or registration page on validation error
     */
    @PostMapping("/provider/register")
    public String register(@RequestParam String token,
                           @RequestParam String name,
                           @RequestParam String password,
                           @RequestParam String confirmPassword,
                           HttpSession session,
                           Model model) {
        String email;

        try {
            email = service.getEmailByToken(token);
        } catch (IllegalArgumentException exception) {
            model.addAttribute("error", exception.getMessage());
            return "login";
        }

        if (!password.equals(confirmPassword)) {
            model.addAttribute("token", token);
            model.addAttribute("email", email);
            model.addAttribute("error", "Passwords do not match");
            return "provider-register";
        }

        try {
            User registeredUser = service.register(token, name, password);
            session.setAttribute("currentUser", registeredUser);
//            model.addAttribute("successMessage", "Registration completed successfully");
//            model.addAttribute("providerEmail", registeredUser.getEmail());
//            return "provider";
            return "redirect:/provider";
        } catch (IllegalArgumentException exception) {
            model.addAttribute("token", token);
            model.addAttribute("email", email);
            model.addAttribute("error", exception.getMessage());
            return "provider-register";
        }
    }
}