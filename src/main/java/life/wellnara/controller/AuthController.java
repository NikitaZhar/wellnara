package life.wellnara.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.security.SecuritySessionService;
import life.wellnara.service.AuthService;
import life.wellnara.service.LoginAttemptService;
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

    private static final String INVALID_CREDENTIALS_MESSAGE = "Неверный логин или пароль";
    private static final String LOCKED_OUT_MESSAGE =
            "Слишком много неудачных попыток входа. Повторите попытку позже.";

    private final AuthService authService;
    private final SecuritySessionService securitySessionService;
    private final LoginAttemptService loginAttemptService;

    /**
     * Creates auth controller.
     *
     * @param authService            authentication service
     * @param securitySessionService service that establishes/clears the security context
     * @param loginAttemptService    guard against login brute-forcing
     */
    public AuthController(AuthService authService,
                          SecuritySessionService securitySessionService,
                          LoginAttemptService loginAttemptService) {
        this.authService = authService;
        this.securitySessionService = securitySessionService;
        this.loginAttemptService = loginAttemptService;
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
     * @param request  current request
     * @param response current response
     * @param model    MVC model
     * @return redirect to role page or login page on error
     */
    @PostMapping("/auth/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        HttpServletRequest request,
                        HttpServletResponse response,
                        Model model) {
        if (loginAttemptService.isBlocked(username)) {
            model.addAttribute("error", LOCKED_OUT_MESSAGE);
            return "login";
        }

        Optional<User> authenticatedUser = authService.authenticate(username, password);

        if (authenticatedUser.isEmpty()) {
            loginAttemptService.recordFailure(username);
            model.addAttribute("error", INVALID_CREDENTIALS_MESSAGE);
            return "login";
        }

        loginAttemptService.reset(username);

        User user = authenticatedUser.get();
        String target = homeRouteForRole(user.getRole());

        if (target == null) {
            model.addAttribute("error", "Неизвестная роль пользователя");
            return "login";
        }

        securitySessionService.establish(user, request, response);
        return target;
    }

    /**
     * Logs out current user.
     *
     * @param request  current request
     * @param response current response
     * @return redirect to login page
     */
    @GetMapping("/auth/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        securitySessionService.clear(request, response);
        return "redirect:/auth/login";
    }

    private String homeRouteForRole(UserRole role) {
        if (role == UserRole.ADMIN) {
            return "redirect:/admin";
        }
        if (role == UserRole.PROVIDER) {
            return "redirect:/home";
        }
        if (role == UserRole.CLIENT) {
            return "redirect:/client";
        }
        return null;
    }
}
