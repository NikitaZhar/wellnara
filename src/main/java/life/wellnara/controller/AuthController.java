package life.wellnara.controller;

import jakarta.servlet.http.HttpSession;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.service.AuthService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

/**
 * Controller for authentication actions.
 */
@Controller
public class AuthController {

    private final AuthService authService;

    /**
     * Creates auth controller.
     *
     * @param authService authentication service
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Shows common login page for all user roles.
     *
     * @return login page view name
     */
    @GetMapping("/auth/login")
    public String showLoginPage() {
        return "login";
    }

    /**
     * Processes login form and redirects user by role.
     *
     * @param username entered username
     * @param password entered password
     * @param session current session
     * @param model MVC model
     * @return redirect to role page or login page on error
     */
    @PostMapping("/auth/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        HttpSession session,
                        Model model) {
        Optional<User> authenticatedUser = authService.authenticate(username, password);

        if (authenticatedUser.isEmpty()) {
            model.addAttribute("error", "Неверный логин или пароль");
            return "login";
        }

        User user = authenticatedUser.get();
        session.setAttribute("currentUser", user);

        if (user.getRole() == UserRole.ADMIN) {
            return "redirect:/admin";
        }

        if (user.getRole() == UserRole.PROVIDER) {
            return "redirect:/provider";
        }

        if (user.getRole() == UserRole.CLIENT) {
            return "redirect:/client";
        }

        session.invalidate();
        model.addAttribute("error", "Неизвестная роль пользователя");
        return "login";
    }

    /**
     * Logs out current user.
     *
     * @param session current session
     * @return redirect to login page
     */
    @GetMapping("/auth/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/auth/login";
    }
}