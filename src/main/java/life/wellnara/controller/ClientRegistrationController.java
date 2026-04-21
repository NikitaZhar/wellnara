package life.wellnara.controller;

import jakarta.servlet.http.HttpSession;
import life.wellnara.model.User;
import life.wellnara.service.ClientInvitationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for client registration by invitation token.
 */
@Controller
public class ClientRegistrationController {

    private final ClientInvitationService clientInvitationService;

    /**
     * Creates client registration controller.
     *
     * @param clientInvitationService service for client invitation flow
     */
    public ClientRegistrationController(ClientInvitationService clientInvitationService) {
        this.clientInvitationService = clientInvitationService;
    }

    /**
     * Shows client registration page.
     *
     * @param token invitation token
     * @param model MVC model
     * @return registration page view name
     */
    @GetMapping("/client/register")
    public String showRegisterPage(@RequestParam String token, Model model) {
        try {
            String email = clientInvitationService.getEmailByToken(token);
            model.addAttribute("token", token);
            model.addAttribute("email", email);
            return "client-register";
        } catch (IllegalArgumentException exception) {
            model.addAttribute("error", exception.getMessage());
            return "login";
        }
    }

    /**
     * Registers client by invitation token.
     *
     * @param token invitation token
     * @param name client name
     * @param password client password
     * @param confirmPassword repeated client password
     * @param session current HTTP session
     * @param model MVC model
     * @return client page on success or registration page on validation error
     */
    @PostMapping("/client/register")
    public String register(@RequestParam String token,
                           @RequestParam String name,
                           @RequestParam String password,
                           @RequestParam String confirmPassword,
                           HttpSession session,
                           Model model) {
        String email;

        try {
            email = clientInvitationService.getEmailByToken(token);
        } catch (IllegalArgumentException exception) {
            model.addAttribute("error", exception.getMessage());
            return "login";
        }

        if (!password.equals(confirmPassword)) {
            model.addAttribute("token", token);
            model.addAttribute("email", email);
            model.addAttribute("error", "Passwords do not match");
            return "client-register";
        }

        try {
            User registeredUser = clientInvitationService.register(token, name, password);
            session.setAttribute("currentUser", registeredUser);

            model.addAttribute("successMessage", "Registration completed successfully");
            model.addAttribute("clientEmail", registeredUser.getEmail());

            return "client";
        } catch (IllegalArgumentException exception) {
            model.addAttribute("token", token);
            model.addAttribute("email", email);
            model.addAttribute("error", exception.getMessage());
            return "client-register";
        }
    }
}