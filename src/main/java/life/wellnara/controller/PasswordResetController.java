package life.wellnara.controller;

import life.wellnara.service.PasswordResetService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for the "forgot password" flow: requesting a reset link by email and
 * choosing a new password from the emailed link.
 */
@Controller
public class PasswordResetController {

    private static final String FORGOT_VIEW = "forgot-password";
    private static final String RESET_VIEW = "reset-password";

    private final PasswordResetService passwordResetService;

    /**
     * Creates password reset controller.
     *
     * @param passwordResetService service driving the reset flow
     */
    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    /**
     * Shows the "enter your email" form.
     *
     * @return forgot-password view name
     */
    @GetMapping("/auth/forgot-password")
    public String showForgotPassword() {
        return FORGOT_VIEW;
    }

    /**
     * Requests a reset link. Always re-renders the page with the same neutral
     * confirmation, whether or not the email belongs to an account.
     *
     * @param email entered email
     * @param model MVC model
     * @return forgot-password view name
     */
    @PostMapping("/auth/forgot-password")
    public String requestReset(@RequestParam String email, Model model) {
        passwordResetService.requestReset(email);
        model.addAttribute("submitted", true);
        return FORGOT_VIEW;
    }

    /**
     * Shows the "choose a new password" form when the link's token is still usable,
     * or an "invalid link" message otherwise.
     *
     * @param token reset token from the link
     * @param model MVC model
     * @return reset-password view name
     */
    @GetMapping("/auth/reset-password")
    public String showResetPassword(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("tokenValid", passwordResetService.isTokenUsable(token));
        return RESET_VIEW;
    }

    /**
     * Consumes the token and sets the new password. On success redirects to the
     * login page; on a bad/expired link shows the "invalid link" state; on a
     * password error re-renders the form with the message and the token preserved.
     *
     * @param token              reset token from the hidden field
     * @param newPassword        new password
     * @param confirmNewPassword repeated new password
     * @param model              MVC model
     * @return redirect to login on success, otherwise the reset-password view
     */
    @PostMapping("/auth/reset-password")
    public String resetPassword(@RequestParam(required = false) String token,
                                @RequestParam(required = false) String newPassword,
                                @RequestParam(required = false) String confirmNewPassword,
                                Model model) {
        try {
            passwordResetService.resetPassword(token, newPassword, confirmNewPassword);
            return "redirect:/auth/login?passwordReset";
        } catch (IllegalStateException invalidToken) {
            model.addAttribute("token", token);
            model.addAttribute("tokenValid", false);
            return RESET_VIEW;
        } catch (IllegalArgumentException passwordError) {
            model.addAttribute("token", token);
            model.addAttribute("tokenValid", true);
            model.addAttribute("error", passwordError.getMessage());
            return RESET_VIEW;
        }
    }
}
