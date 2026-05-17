package life.wellnara.controller;

import jakarta.servlet.http.HttpSession;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.service.AuthService;
import life.wellnara.service.SessionUserService;
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
    private final SessionUserService sessionUserService;

    /**
     * Creates auth controller.
     *
     * @param authService authentication service
     * @param sessionUserService service for session user access
     */
    public AuthController(AuthService authService,
                          SessionUserService sessionUserService) {
        this.authService = authService;
        this.sessionUserService = sessionUserService;
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
        sessionUserService.login(session, user);

        if (user.getRole() == UserRole.ADMIN) {
            return "redirect:/admin";
        }

        if (user.getRole() == UserRole.PROVIDER) {
            return "redirect:/provider";
        }

        if (user.getRole() == UserRole.CLIENT) {
            return "redirect:/client";
        }

        sessionUserService.logout(session);
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
        sessionUserService.logout(session);
        return "redirect:/auth/login";
    }
}